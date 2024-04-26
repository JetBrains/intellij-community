// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.codeInsight;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public final class JavaFxGetterSetterPrototypeProvider extends GetterSetterPrototypeProvider {
  private static final Logger LOG = Logger.getInstance(JavaFxGetterSetterPrototypeProvider.class);

  @Override
  public boolean canGeneratePrototypeFor(PsiField field) {
    return field.isValid() && InheritanceUtil.isInheritor(field.getType(), JavaFxCommonNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE) &&
           JavaFxPsiUtil.getWrappedPropertyType(field, field.getProject(), JavaFxCommonNames.ourReadOnlyMap) != null;
  }

  @Override
  public PsiMethod[] generateGetters(PsiField field) {
    final Project project = field.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiMethod getter = GenerateMembersUtil.generateSimpleGetterPrototype(field);

    final PsiType wrappedType = JavaFxPsiUtil.getWrappedPropertyType(field, project, JavaFxCommonNames.ourReadOnlyMap);
    LOG.assertTrue(wrappedType != null, field.getType());
    getter.setName(PropertyUtilBase.suggestGetterName(PropertyUtilBase.suggestPropertyName(field), wrappedType));

    final PsiTypeElement returnTypeElement = getter.getReturnTypeElement();
    LOG.assertTrue(returnTypeElement != null);
    returnTypeElement.replace(factory.createTypeElement(wrappedType));

    final PsiCodeBlock getterBody = getter.getBody();
    LOG.assertTrue(getterBody != null);
    final String fieldName = field.getName();
    getterBody.getStatements()[0].replace(factory.createStatementFromText("return " + fieldName + ".get();", field));

    final PsiMethod propertyGetter = PropertyUtilBase.generateGetterPrototype(field);
    propertyGetter.setName(JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(fieldName, VariableKind.FIELD) + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX);
    return new PsiMethod[] {getter, GenerateMembersUtil.annotateOnOverrideImplement(field.getContainingClass(), propertyGetter)};
  }

  @Override
  public PsiMethod[] generateSetters(PsiField field) {
    final PsiMethod setter = GenerateMembersUtil.generateSimpleSetterPrototype(field);
    final Project project = field.getProject();

    final PsiType wrappedType = JavaFxPsiUtil.getWrappedPropertyType(field, project, JavaFxCommonNames.ourWritableMap);
    LOG.assertTrue(wrappedType != null, field.getType());
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiTypeElement newTypeElement = elementFactory.createTypeElement(wrappedType);
    final PsiParameter[] parameters = setter.getParameterList().getParameters();
    LOG.assertTrue(parameters.length == 1);
    final PsiParameter parameter = parameters[0];
    final PsiTypeElement typeElement = parameter.getTypeElement();
    LOG.assertTrue(typeElement != null);
    typeElement.replace(newTypeElement);
    final PsiCodeBlock body = setter.getBody();
    LOG.assertTrue(body != null);
    body.getStatements()[0].replace(elementFactory.createStatementFromText("this." + field.getName() + ".set(" + parameter.getName() + ");", field));

    return new PsiMethod[] {setter};
  }

  @Override
  public PsiMethod[] findGetters(PsiClass psiClass, String propertyName) {
    final String getterName = suggestGetterName(propertyName);
    PsiMethod specificGetter = findGetterByName(psiClass, getterName);
    if (specificGetter != null) {
      final PsiMethod getter = PropertyUtilBase.findPropertyGetter(psiClass, propertyName, false, false);
      return getter == null ? new PsiMethod[] {specificGetter} : new PsiMethod[] {getter, specificGetter};
    }
    return super.findGetters(psiClass, propertyName);
  }

  private static @Nullable PsiMethod findGetterByName(PsiClass psiClass, String getterName) {
    for (PsiMethod candidate : psiClass.findMethodsByName(getterName, false)) {
      if (candidate.getParameterList().isEmpty() && 
          !candidate.hasModifierProperty(PsiModifier.STATIC)) {
        return candidate;
      }
    }
    return null;
  }

  @Override
  public String suggestGetterName(String propertyName) {
    return propertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX;
  }

  @Override
  public boolean isSimpleGetter(PsiMethod method, String oldPropertyName) {
    return method.getName().equals(suggestGetterName(oldPropertyName));
  }

  @Override
  public boolean isReadOnly(PsiField field) {
    return !InheritanceUtil.isInheritor(field.getType(), JavaFxCommonNames.JAVAFX_BEANS_VALUE_WRITABLE_VALUE);
  }
}
