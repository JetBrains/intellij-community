// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.intellij.util.xmlb.annotations.Property.Style.ATTRIBUTE;

public final class ExtensionPointBinding {

  private final PsiClass myPsiClass;

  public ExtensionPointBinding(@NotNull PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  public void visit(BindingVisitor visitor) {
    NotNullLazyValue<Boolean> hasClassLevelPropertyAnnotation = NotNullLazyValue.lazy(() -> hasClassLevelPropertyAnnotation());

    for (PsiField field : myPsiClass.getAllFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC) ||
          field.hasModifierProperty(PsiModifier.VOLATILE)) {
        continue;
      }

      NullableLazyValue<PsiMethod> getter = NullableLazyValue.lazyNullable(() -> PropertyUtilBase.findGetterForField(field));
      NullableLazyValue<PsiMethod> setter = NullableLazyValue.lazyNullable(() -> PropertyUtilBase.findSetterForField(field));

      if (!field.hasModifierProperty(PsiModifier.PUBLIC) && (getter.getValue() == null || setter.getValue() == null)) {
        continue;
      }

      final PsiAnnotation requiredAnnotation = findAnnotationForField(RequiredElement.class, field, getter, setter);
      BindingVisitor.RequiredFlag required = BindingVisitor.RequiredFlag.NOT_REQUIRED;
      if (requiredAnnotation != null) {
        required = PsiUtil.getAnnotationBooleanAttribute(requiredAnnotation, "allowEmpty") ?
                   BindingVisitor.RequiredFlag.REQUIRED_ALLOW_EMPTY : BindingVisitor.RequiredFlag.REQUIRED;
      }

      final PsiAnnotation attributeAnnotation = findAnnotationForField(Attribute.class, field, getter, setter);
      if (attributeAnnotation != null) {
        String fieldName = PsiUtil.getAnnotationStringAttribute(attributeAnnotation, "value", field.getName());
        if (fieldName != null) {
          visitor.visitAttribute(field, fieldName, required);
        }
      }
      else if (hasClassLevelPropertyAnnotation.get()) {
        visitor.visitAttribute(field, field.getName(), required);
      }
      else {
        final PsiAnnotation tagAnno = findAnnotationForField(Tag.class, field, getter, setter);
        final PsiAnnotation collectionAnnotation = findAnnotationForField(XCollection.class, field, getter, setter);
        //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo

        String fieldName = field.getName();
        final String tagName;
        if (tagAnno != null) {
          tagName = PsiUtil.getAnnotationStringAttribute(tagAnno, "value", fieldName);
        }
        else {
          final PsiAnnotation propAnno = findAnnotationForField(Property.class, field, getter, setter);
          tagName = propAnno != null && PsiUtil.getAnnotationBooleanAttribute(propAnno, "surroundWithTag") ? Constants.OPTION : null;
        }

        if (tagName != null && collectionAnnotation == null) {
          visitor.visitTagOrProperty(field, tagName, required);
        }
        else if (collectionAnnotation != null) {
          visitor.visitXCollection(field, tagName, collectionAnnotation, required);
        }
      }
    }
  }

  private static @Nullable PsiAnnotation findAnnotationForField(final Class<?> annotationClass,
                                                                PsiField field,
                                                                NullableLazyValue<PsiMethod> fieldGetter,
                                                                NullableLazyValue<PsiMethod> fieldSetter) {
    PsiAnnotation annotation = PsiUtil.findAnnotation(annotationClass, field);
    if (annotation != null) {
      return annotation;
    }

    annotation = PsiUtil.findAnnotation(annotationClass, fieldGetter.getValue());
    if (annotation != null) return annotation;

    return PsiUtil.findAnnotation(annotationClass, fieldSetter.getValue());
  }

  @ApiStatus.OverrideOnly
  public interface BindingVisitor {

    enum RequiredFlag {
      NOT_REQUIRED,
      REQUIRED,
      REQUIRED_ALLOW_EMPTY
    }

    void visitAttribute(@NotNull PsiField field, @NotNull @NonNls String attributeName, RequiredFlag required);

    void visitTagOrProperty(@NotNull PsiField field, @NotNull @NonNls String tagName, RequiredFlag required);

    void visitXCollection(@NotNull PsiField field,
                          @Nullable @NonNls String tagName,
                          @NotNull PsiAnnotation collectionAnnotation,
                          RequiredFlag required);
  }

  private boolean hasClassLevelPropertyAnnotation() {
    final PsiAnnotation propertyAnnotation = myPsiClass.getAnnotation(Property.class.getName());
    if (propertyAnnotation == null) return false;
    final PsiNameValuePair style = AnnotationUtil.findDeclaredAttribute(propertyAnnotation, "style");
    if (style == null) return false;

    final PsiReferenceExpression referenceExpression = tryCast(style.getDetachedValue(), PsiReferenceExpression.class);
    if (referenceExpression == null) return false;
    final PsiEnumConstant enumConstant = tryCast(referenceExpression.resolve(), PsiEnumConstant.class);
    return enumConstant != null && enumConstant.getName().equals(ATTRIBUTE.name());
  }
}
