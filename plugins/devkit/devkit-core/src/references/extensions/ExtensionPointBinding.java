// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

public class ExtensionPointBinding {

  private final PsiClass myPsiClass;

  public ExtensionPointBinding(@NotNull PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  public void visit(BindingVisitor visitor) {
    PsiField[] fields;
    UClass beanClassNavigationClass = UastContextKt.toUElement(myPsiClass.getNavigationElement(), UClass.class);
    if (beanClassNavigationClass != null) {
      fields = beanClassNavigationClass.getAllFields();
    }
    else {
      fields = myPsiClass.getAllFields(); // fallback
    }

    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
      final PsiMethod getter = PropertyUtilBase.findGetterForField(field);
      final PsiMethod setter = PropertyUtilBase.findSetterForField(field);
      if ((getter == null || setter == null) && !field.hasModifierProperty(PsiModifier.PUBLIC)) continue;

      boolean required = PsiUtil.findAnnotation(RequiredElement.class, field, getter, setter) != null;
      final PsiAnnotation attributeAnnotation = PsiUtil.findAnnotation(Attribute.class, field, getter, setter);
      if (attributeAnnotation != null) {
        String fieldName = PsiUtil.getAnnotationStringAttribute(attributeAnnotation, "value", field.getName());
        if (fieldName != null) {
          visitor.visitAttribute(field, fieldName, required);
        }
      }
      else {
        final PsiAnnotation tagAnno = PsiUtil.findAnnotation(Tag.class, field, getter, setter);
        final PsiAnnotation collectionAnnotation = PsiUtil.findAnnotation(XCollection.class, field, getter, setter);
        //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo

        String fieldName = field.getName();
        final String tagName;
        if (tagAnno != null) {
          tagName = PsiUtil.getAnnotationStringAttribute(tagAnno, "value", fieldName);
        }
        else {
          final PsiAnnotation propAnno = PsiUtil.findAnnotation(Property.class, field, getter, setter);
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

  public interface BindingVisitor {

    void visitAttribute(@NotNull PsiField field, @NotNull String attributeName, boolean required);

    void visitTagOrProperty(@NotNull PsiField field, @NotNull String tagName, boolean required);

    void visitXCollection(@NotNull PsiField field, @Nullable String tagName, @NotNull PsiAnnotation collectionAnnotation, boolean required);
  }
}
