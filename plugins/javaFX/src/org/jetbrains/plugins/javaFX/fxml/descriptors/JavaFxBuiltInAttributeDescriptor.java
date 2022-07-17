// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.List;

public class JavaFxBuiltInAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private static final Logger LOG = Logger.getInstance(JavaFxBuiltInAttributeDescriptor.class);

  private final String myParentTagName;

  private JavaFxBuiltInAttributeDescriptor(String name, PsiClass psiClass) {
    super(name, psiClass);
    myParentTagName = null;
  }

  private JavaFxBuiltInAttributeDescriptor(String name, String parentTagName) {
    super(name, null);
    myParentTagName = parentTagName;
  }

  public static JavaFxBuiltInAttributeDescriptor create(String name, PsiClass psiClass) {
    if (FxmlConstants.FX_ID.equals(name)) return new FxIdAttributeDescriptor(psiClass);
    if (FxmlConstants.FX_VALUE.equals(name)) return new FxValueAttributeDescriptor(psiClass);
    if (FxmlConstants.FX_CONSTANT.equals(name)) return new FxConstantAttributeDescriptor(psiClass);
    return new JavaFxBuiltInAttributeDescriptor(name, psiClass);
  }

  public static JavaFxBuiltInAttributeDescriptor create(String name, String parentTagName) {
    if (FxmlConstants.FX_ID.equals(name)) return new FxIdAttributeDescriptor(parentTagName);
    if (FxmlConstants.FX_VALUE.equals(name)) return new FxValueAttributeDescriptor(parentTagName);
    if (FxmlConstants.FX_CONSTANT.equals(name)) return new FxConstantAttributeDescriptor(parentTagName);
    return new JavaFxBuiltInAttributeDescriptor(name, parentTagName);
  }

  @Override
  public boolean isEnumerated() {
    return false;
  }

  @Override
  public boolean isRequired() {
    if (myParentTagName == null) return super.isRequired();
    final List<String> requiredAttrs = FxmlConstants.FX_BUILT_IN_TAG_REQUIRED_ATTRIBUTES.get(myParentTagName);
    return requiredAttrs != null && requiredAttrs.contains(getName());
  }

  @Override
  public String toString() {
    return myParentTagName != null ? myParentTagName + "#" + getName() : super.toString();
  }


  private static final class FxIdAttributeDescriptor extends JavaFxBuiltInAttributeDescriptor {
    private FxIdAttributeDescriptor(PsiClass psiClass) {
      super(FxmlConstants.FX_ID, psiClass);
    }

    private FxIdAttributeDescriptor(String parentTagName) {
      super(FxmlConstants.FX_ID, parentTagName);
    }

    @Override
    public boolean hasIdType() {
      return true;
    }

    @Nullable
    @Override
    protected String validateAttributeValue(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
      final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(xmlAttributeValue.getContainingFile());
      if (controllerClass != null) {
        final PsiClass tagClass = JavaFxPsiUtil.getTagClass(xmlAttributeValue);
        if (tagClass != null) {
          final PsiField field = controllerClass.findFieldByName(value, true);
          if (field != null && !InheritanceUtil.isInheritorOrSelf(tagClass, PsiUtil.resolveClassInType(field.getType()), true)) {
            return JavaFXBundle.message("cannot.class.name.to.field.name", tagClass.getQualifiedName(), field.getName());
          }
        }
      }
      return null;
    }
  }

  private static final class FxValueAttributeDescriptor extends JavaFxBuiltInAttributeDescriptor {
    private FxValueAttributeDescriptor(PsiClass psiClass) {
      super(FxmlConstants.FX_VALUE, psiClass);
    }

    private FxValueAttributeDescriptor(String parentTagName) {
      super(FxmlConstants.FX_VALUE, parentTagName);
    }

    @Override
    public boolean isEnumerated() {
      final PsiClass psiClass = getPsiClass();
      return psiClass != null && psiClass.isEnum();
    }

    @Override
    protected PsiClass getEnum() {
      final PsiClass psiClass = getPsiClass();
      return psiClass.isEnum() ? psiClass : null;
    }

    @Nullable
    @Override
    protected String validateAttributeValue(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
      final PsiClass tagClass = JavaFxPsiUtil.getTagClass(xmlAttributeValue);
      if (tagClass != null) {
        if (tagClass.isEnum()) {
          return JavaFxPsiUtil.validateEnumConstant(tagClass, value);
        }
        final PsiMethod method = JavaFxPsiUtil.findValueOfMethod(tagClass);
        if (method == null) {
          return JavaFXBundle.message("unable.to.coerce.error",value, tagClass.getQualifiedName());
        }
      }
      return validateLiteral(xmlAttributeValue, value);
    }
  }

  private static final class FxConstantAttributeDescriptor extends JavaFxBuiltInAttributeDescriptor {
    private FxConstantAttributeDescriptor(PsiClass psiClass) {
      super(FxmlConstants.FX_CONSTANT, psiClass);
    }

    private FxConstantAttributeDescriptor(String parentTagName) {
      super(FxmlConstants.FX_CONSTANT, parentTagName);
    }

    @Override
    public boolean isEnumerated() {
      return getPsiClass() != null;
    }

    @Override
    protected PsiClass getEnum() {
      return getPsiClass();
    }

    @Override
    protected boolean isConstant(PsiField field) {
      return field.hasModifierProperty(PsiModifier.STATIC) &&
             field.hasModifierProperty(PsiModifier.FINAL) &&
             field.hasModifierProperty(PsiModifier.PUBLIC);
    }

    @Nullable
    @Override
    protected String validateAttributeValue(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
      final PsiClass tagClass = JavaFxPsiUtil.getTagClass(xmlAttributeValue);
      if (tagClass != null) {
        final PsiField constField = tagClass.findFieldByName(value, true);
        if (constField == null || !isConstant(constField)) {
          return JavaFXBundle.message("constant.not.found", value);
        }
      }
      return null;
    }
  }
}
