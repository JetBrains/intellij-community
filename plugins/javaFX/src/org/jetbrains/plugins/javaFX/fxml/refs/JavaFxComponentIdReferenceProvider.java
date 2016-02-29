/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
* User: anna
*/
class JavaFxComponentIdReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull ProcessingContext context) {
    final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
    final XmlTag currentTag = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlTag.class);
    final String value = xmlAttributeValue.getValue();
    final boolean startsWithDollar = value.startsWith("$");
    final String referencesId = startsWithDollar ? value.substring(1) : value;
    final Map<String, XmlAttributeValue> fileIds = JavaFxPsiUtil.collectFileIds(currentTag);

    if (JavaFxPsiUtil.isExpressionBinding(value)) {
      final String expressionText = referencesId.substring(1, referencesId.length() - 1);
      final String newId = StringUtil.getPackageName(expressionText);
      final String fieldRef = StringUtil.getShortName(expressionText);

      final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(element.getContainingFile());
      final PsiReferenceBase idReferenceBase = getIdReferenceBase(xmlAttributeValue, newId, fileIds, Collections.emptyMap(), controllerClass);
      final TextRange range = idReferenceBase.getRangeInElement();
      final int startOffset = range.getStartOffset() + 2;
      final int endOffset = startOffset + newId.length();
      idReferenceBase.setRangeInElement(new TextRange(startOffset, endOffset));
      if (fileIds.containsKey(newId)) {
        final PsiClass tagClass = FxmlConstants.CONTROLLER.equals(newId) ? controllerClass : JavaFxPsiUtil.getTagClass(fileIds.get(newId));
        if (tagClass != null) {
          final JavaFxExpressionReferenceBase referenceBase = new JavaFxExpressionReferenceBase(xmlAttributeValue, tagClass, fieldRef);
          final TextRange textRange = referenceBase.getRangeInElement();
          referenceBase.setRangeInElement(new TextRange(endOffset + 1, textRange.getEndOffset() - 1));
          return new PsiReference[] {idReferenceBase, referenceBase};
        }
      }
      return new PsiReference[] {idReferenceBase};
    }
    if (startsWithDollar) {
      final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(element.getContainingFile());

      final PsiClass targetPropertyClass = JavaFxPsiUtil.getPropertyClass(xmlAttributeValue);
      final boolean isConvertible = targetPropertyClass != null && JavaFxPsiUtil.hasConversionFromAnyType(targetPropertyClass);

      final Map<String, TypeMatch> typeMatches = fileIds.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> {
          final PsiClass valueClass = JavaFxPsiUtil.getTagClassById(e.getKey(), xmlAttributeValue, e.getValue());
          return TypeMatch.getMatch(valueClass, targetPropertyClass, isConvertible);
        }));

      final PsiReferenceBase idReferenceBase = getIdReferenceBase(xmlAttributeValue, referencesId, fileIds, typeMatches, controllerClass);
      final TextRange rangeInElement = idReferenceBase.getRangeInElement();
      idReferenceBase.setRangeInElement(new TextRange(rangeInElement.getStartOffset() + 1, rangeInElement.getEndOffset()));
      return new PsiReference[]{idReferenceBase};
    } else {
      final Set<String> acceptableIds = new HashSet<String>();
      if (currentTag != null) {
        final XmlTag parentTag = currentTag.getParentTag();
        for (final String id : fileIds.keySet()) {
          final XmlAttributeValue resolvedAttrValue = fileIds.get(id);
          if (JavaFxPsiUtil.isClassAcceptable(parentTag, JavaFxPsiUtil.getTagClass(resolvedAttrValue)) == null) {
            acceptableIds.add(id);
          }
        }
      }
      JavaFxIdReferenceBase idReferenceBase = new JavaFxIdReferenceBase(xmlAttributeValue, fileIds, acceptableIds, referencesId);
      return new PsiReference[]{idReferenceBase};
    }
  }

  @NotNull
  private static PsiReferenceBase getIdReferenceBase(XmlAttributeValue xmlAttributeValue,
                                                     String referencesId,
                                                     Map<String, XmlAttributeValue> fileIds,
                                                     Map<String, TypeMatch> typeMatches,
                                                     PsiClass controllerClass) {
    if (controllerClass != null && !FxmlConstants.CONTROLLER.equals(referencesId)) {
      final PsiField controllerField = controllerClass.findFieldByName(referencesId, false);
      if (controllerField != null) {
        return new JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef(xmlAttributeValue, controllerField, controllerClass);
      }
    }
    return new JavaFxIdReferenceBase(xmlAttributeValue, fileIds, typeMatches, referencesId);
  }

  private enum TypeMatch {
    ASSIGNABLE(3.0),
    CONVERTIBLE(2.0),
    UNDEFINED(1.0),
    INCOMPATIBLE(0.0);

    private final double myPriority;

    TypeMatch(double priority) {
      myPriority = priority;
    }

    public static double getPriority(TypeMatch match) {
      return match != null ? match.myPriority : 0.0;
    }

    @NotNull
    public static TypeMatch getMatch(PsiClass valueClass, PsiClass targetPropertyClass, boolean isConvertible) {
      if (valueClass == null || targetPropertyClass == null) return UNDEFINED;
      if (InheritanceUtil.isInheritorOrSelf(valueClass, targetPropertyClass, true)) return ASSIGNABLE;
      if (isConvertible) return CONVERTIBLE;
      return INCOMPATIBLE;
    }
  }

  private static class JavaFxIdReferenceBase extends PsiReferenceBase<XmlAttributeValue> {
    private final Map<String, XmlAttributeValue> myFileIds;
    private final Set<String> myAcceptableIds;
    private final Map<String, TypeMatch> myTypeMatches;
    private final String myReferencesId;

    private JavaFxIdReferenceBase(XmlAttributeValue element,
                                  Map<String, XmlAttributeValue> fileIds, 
                                  Set<String> acceptableIds, 
                                  String referencesId) {
      super(element);
      myFileIds = fileIds;
      myAcceptableIds = acceptableIds;
      myReferencesId = referencesId;
      myTypeMatches = Collections.emptyMap();
    }

    public JavaFxIdReferenceBase(XmlAttributeValue xmlAttributeValue,
                                 Map<String, XmlAttributeValue> fileIds,
                                 Map<String, TypeMatch> typeMatches,
                                 String referencesId) {
      super(xmlAttributeValue);
      myFileIds = fileIds;
      myTypeMatches = typeMatches;
      myReferencesId = referencesId;
      myAcceptableIds = myFileIds.keySet();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myFileIds.get(myReferencesId);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return myAcceptableIds.stream()
        .map(id -> PrioritizedLookupElement.withPriority(LookupElementBuilder.create(id), TypeMatch.getPriority(myTypeMatches.get(id))))
        .toArray(LookupElement[]::new);
    }
  }

  private static class JavaFxExpressionReferenceBase extends PsiReferenceBase<XmlAttributeValue> {
    private final PsiClass myTagClass;
    private final String myFieldName;

    public JavaFxExpressionReferenceBase(XmlAttributeValue xmlAttributeValue, PsiClass tagClass, String fieldName) {
      super(xmlAttributeValue);
      myTagClass = tagClass;
      myFieldName = fieldName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myTagClass.findFieldByName(myFieldName, true);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final PsiElement parent = getElement().getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor != null) {
          final PsiElement declaration = descriptor.getDeclaration();
          if (declaration instanceof PsiField) {
            return collectProperties((PsiField)declaration);
          }
        }
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private Object[] collectProperties(@NotNull PsiField psiField) {
      final PsiType type = psiField.getType();
      final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(type, psiField.getProject());
      final List<PsiField> objs = new ArrayList<PsiField>();
      for (PsiField field : myTagClass.getAllFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        final PsiType fieldType = field.getType();
        if (TypeConversionUtil.isAssignable(type, fieldType) || (propertyType != null && TypeConversionUtil.isAssignable(propertyType, fieldType))) {
          objs.add(field);
        }
      }
      return ArrayUtil.toObjectArray(objs);
    }
  }
}
