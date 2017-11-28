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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

import java.util.*;
import java.util.stream.Collectors;

public class JavaFxComponentIdReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull ProcessingContext context) {
    final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
    final String value = xmlAttributeValue.getValue();
    if (JavaFxPsiUtil.isIncorrectExpressionBinding(value)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlTag currentTag = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlTag.class);
    final Map<String, XmlAttributeValue> fileIds = JavaFxPsiUtil.collectFileIds(currentTag);

    if (JavaFxPsiUtil.isExpressionBinding(value)) {
      return getExpressionReferences(element, xmlAttributeValue, value, fileIds);
    }
    if (value.startsWith("$")) {
      return getSinglePropertyReferences(xmlAttributeValue, value, fileIds);
    }
    final Set<String> acceptableIds = new HashSet<>();
    if (currentTag != null) {
      final XmlTag parentTag = currentTag.getParentTag();
      for (final String id : fileIds.keySet()) {
        final XmlAttributeValue resolvedAttrValue = fileIds.get(id);
        if (JavaFxPsiUtil.isClassAcceptable(parentTag, JavaFxPsiUtil.getTagClass(resolvedAttrValue))) {
          acceptableIds.add(id);
        }
      }
    }
    return new PsiReference[]{new JavaFxIdReferenceBase(xmlAttributeValue, fileIds, acceptableIds, value)};
  }

  @NotNull
  private static PsiReference[] getExpressionReferences(@NotNull PsiElement element,
                                                        @NotNull XmlAttributeValue xmlAttributeValue,
                                                        @NotNull String value,
                                                        @NotNull Map<String, XmlAttributeValue> fileIds) {
    if (FxmlConstants.NULL_EXPRESSION.equals(value)) return PsiReference.EMPTY_ARRAY;
    final String expressionBody = value.substring(2, value.length() - 1);
    final List<String> propertyNames = StringUtil.split(expressionBody, ".", true, false);
    if (JavaFxPropertyAttributeDescriptor.isIncompletePropertyChain(propertyNames)) return PsiReference.EMPTY_ARRAY;
    if (propertyNames.size() == 1) {
      return getSinglePropertyReferences(xmlAttributeValue, fileIds, expressionBody, 2);
    }

    final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(element.getContainingFile());
    final String firstPropertyName = propertyNames.get(0);
    int positionInExpression = 2;
    final List<PsiReference> result = new ArrayList<>();
    final PsiReferenceBase firstReference =
      getIdReferenceBase(xmlAttributeValue, firstPropertyName, fileIds, Collections.emptyMap(), controllerClass);
    positionInExpression = adjustTextRange(firstPropertyName, firstReference, positionInExpression);
    PsiClass propertyOwnerClass = FxmlConstants.CONTROLLER.equals(firstPropertyName) ?
                                  controllerClass : JavaFxPsiUtil.getTagClass(fileIds.get(firstPropertyName));
    result.add(firstReference);

    final List<String> remainingPropertyNames = propertyNames.subList(1, propertyNames.size());
    for (String propertyName : remainingPropertyNames) {
      final JavaFxExpressionReferenceBase reference =
        new JavaFxExpressionReferenceBase(xmlAttributeValue, propertyOwnerClass, propertyName);
      positionInExpression = adjustTextRange(propertyName, reference, positionInExpression);
      final PsiType propertyType = JavaFxPsiUtil.getReadablePropertyType(reference.resolve());
      propertyOwnerClass = propertyType instanceof PsiClassType ? ((PsiClassType)propertyType).resolve() : null;
      result.add(reference);
    }
    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  @NotNull
  private static PsiReference[] getSinglePropertyReferences(@NotNull XmlAttributeValue xmlAttributeValue,
                                                            @NotNull String value,
                                                            @NotNull Map<String, XmlAttributeValue> fileIds) {
    if (FxmlConstants.isNullValue(value)) return PsiReference.EMPTY_ARRAY;
    return getSinglePropertyReferences(xmlAttributeValue, fileIds, value.substring(1), 1);
  }

  @NotNull
  private static PsiReference[] getSinglePropertyReferences(@NotNull XmlAttributeValue xmlAttributeValue,
                                                            @NotNull Map<String, XmlAttributeValue> fileIds,
                                                            @NotNull String propertyName,
                                                            int positionInExpression) {
    final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(xmlAttributeValue.getContainingFile());
    final Map<String, TypeMatch> typeMatches = getTypeMatches(xmlAttributeValue, fileIds);
    final PsiReferenceBase reference = getIdReferenceBase(xmlAttributeValue, propertyName, fileIds, typeMatches, controllerClass);
    adjustTextRange(propertyName, reference, positionInExpression);
    return new PsiReference[]{reference};
  }

  private static Map<String, TypeMatch> getTypeMatches(@NotNull XmlAttributeValue xmlAttributeValue,
                                                       @NotNull Map<String, XmlAttributeValue> fileIds) {
    final PsiClass targetPropertyClass = JavaFxPsiUtil.getWritablePropertyClass(xmlAttributeValue);
    final boolean isConvertible = targetPropertyClass != null && JavaFxPsiUtil.hasConversionFromAnyType(targetPropertyClass);

    return fileIds.entrySet().stream().collect(
      Collectors.toMap(Map.Entry::getKey, e -> {
        final PsiClass valueClass = JavaFxPsiUtil.getTagClassById(e.getValue(), e.getKey(), xmlAttributeValue);
        return TypeMatch.getMatch(valueClass, targetPropertyClass, isConvertible);
      }));
  }

  private static int adjustTextRange(@NotNull String propertyName, @NotNull PsiReferenceBase reference, int positionInExpression) {
    final TextRange range = reference.getRangeInElement();
    final int startOffset = range.getStartOffset() + positionInExpression;
    final int endOffset = startOffset + propertyName.length();
    reference.setRangeInElement(new TextRange(startOffset, endOffset));
    return positionInExpression + propertyName.length() + 1;
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

  public static class JavaFxIdReferenceBase extends PsiReferenceBase<XmlAttributeValue> {
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

    public boolean isBuiltIn() {
      return FxmlConstants.CONTROLLER.equals(myReferencesId) || myReferencesId.endsWith(FxmlConstants.CONTROLLER_SUFFIX);
    }
  }

  private static class JavaFxExpressionReferenceBase extends JavaFxPropertyReference<XmlAttributeValue> {
    private final String myFieldName;

    public JavaFxExpressionReferenceBase(@NotNull XmlAttributeValue xmlAttributeValue, PsiClass tagClass, @NotNull String fieldName) {
      super(xmlAttributeValue, tagClass);
      myFieldName = fieldName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return JavaFxPsiUtil.getReadableProperties(myPsiClass).get(myFieldName);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final XmlAttributeValue xmlAttributeValue = getElement();
      final PsiElement declaration = JavaFxPsiUtil.getAttributeDeclaration(xmlAttributeValue);
      final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(myPsiClass, declaration);
      if (propertyType != null) {
        return collectProperties(propertyType, xmlAttributeValue.getProject());
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private Object[] collectProperties(@NotNull PsiType propertyType, @NotNull Project project) {
      final PsiType resolvedType = JavaFxPsiUtil.getWritablePropertyType(propertyType, project);
      final List<LookupElement> objs = new ArrayList<>();
      final Collection<PsiMember> readableProperties = JavaFxPsiUtil.getReadableProperties(myPsiClass).values();
      for (PsiMember readableMember : readableProperties) {
        final PsiType readableType = JavaFxPsiUtil.getReadablePropertyType(readableMember);
        if (readableType == null) continue;
        if (TypeConversionUtil.isAssignable(propertyType, readableType) ||
            resolvedType != null && TypeConversionUtil.isAssignable(resolvedType, readableType)) {
          final String propertyName = PropertyUtilBase.getPropertyName(readableMember);
          if (propertyName != null) {
            objs.add(LookupElementBuilder.create(readableMember, propertyName));
          }
        }
      }
      return ArrayUtil.toObjectArray(objs);
    }

    @NotNull
    @Override
    public String getPropertyName() {
      return myFieldName;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, resolve() instanceof PsiMethod);
      return super.handleElementRename(newPropertyName);
    }
  }
}
