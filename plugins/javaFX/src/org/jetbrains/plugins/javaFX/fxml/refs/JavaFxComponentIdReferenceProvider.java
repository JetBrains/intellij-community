// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

import java.util.*;
import java.util.stream.Collectors;

public final class JavaFxComponentIdReferenceProvider extends PsiReferenceProvider {

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
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

  private static PsiReference @NotNull [] getExpressionReferences(@NotNull PsiElement element,
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

  private static PsiReference @NotNull [] getSinglePropertyReferences(@NotNull XmlAttributeValue xmlAttributeValue,
                                                                      @NotNull String value,
                                                                      @NotNull Map<String, XmlAttributeValue> fileIds) {
    if (FxmlConstants.isNullValue(value)) return PsiReference.EMPTY_ARRAY;
    return getSinglePropertyReferences(xmlAttributeValue, fileIds, value.substring(1), 1);
  }

  private static PsiReference @NotNull [] getSinglePropertyReferences(@NotNull XmlAttributeValue xmlAttributeValue,
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

  private static @NotNull PsiReferenceBase getIdReferenceBase(XmlAttributeValue xmlAttributeValue,
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

    public static @NotNull TypeMatch getMatch(PsiClass valueClass, PsiClass targetPropertyClass, boolean isConvertible) {
      if (valueClass == null || targetPropertyClass == null) return UNDEFINED;
      if (InheritanceUtil.isInheritorOrSelf(valueClass, targetPropertyClass, true)) return ASSIGNABLE;
      if (isConvertible) return CONVERTIBLE;
      return INCOMPATIBLE;
    }
  }

  public static final class JavaFxIdReferenceBase extends PsiReferenceBase<XmlAttributeValue> {
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

    @Override
    public @Nullable PsiElement resolve() {
      return myFileIds.get(myReferencesId);
    }

    @Override
    public Object @NotNull [] getVariants() {
      return myAcceptableIds.stream()
        .map(id -> PrioritizedLookupElement.withPriority(LookupElementBuilder.create(id), TypeMatch.getPriority(myTypeMatches.get(id))))
        .toArray(LookupElement[]::new);
    }

    public boolean isBuiltIn() {
      return FxmlConstants.CONTROLLER.equals(myReferencesId) || myReferencesId.endsWith(FxmlConstants.CONTROLLER_SUFFIX);
    }
  }

  private static final class JavaFxExpressionReferenceBase extends JavaFxPropertyReference<XmlAttributeValue> {
    private final String myFieldName;

    JavaFxExpressionReferenceBase(@NotNull XmlAttributeValue xmlAttributeValue, PsiClass tagClass, @NotNull String fieldName) {
      super(xmlAttributeValue, tagClass);
      myFieldName = fieldName;
    }

    @Override
    public @Nullable PsiElement resolve() {
      return JavaFxPsiUtil.getReadableProperties(myPsiClass).get(myFieldName);
    }

    @Override
    public Object @NotNull [] getVariants() {
      final XmlAttributeValue xmlAttributeValue = getElement();
      final PsiElement declaration = JavaFxPsiUtil.getAttributeDeclaration(xmlAttributeValue);
      final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(myPsiClass, declaration);
      if (propertyType != null) {
        return collectProperties(propertyType, xmlAttributeValue.getProject());
      }
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
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

    @Override
    public @NotNull String getPropertyName() {
      return myFieldName;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, resolve() instanceof PsiMethod);
      return super.handleElementRename(newPropertyName);
    }
  }
}
