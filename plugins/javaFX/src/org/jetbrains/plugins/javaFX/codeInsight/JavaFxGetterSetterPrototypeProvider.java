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
package org.jetbrains.plugins.javaFX.codeInsight;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * User: anna
 * Date: 3/4/13
 */
public class JavaFxGetterSetterPrototypeProvider extends GetterSetterPrototypeProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxGetterSetterPrototypeProvider.class.getName());

  @Override
  public boolean canGeneratePrototypeFor(PsiField field) {
    return InheritanceUtil.isInheritor(field.getType(), JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE);
  }

  @Override
  public PsiMethod[] generateGetters(PsiField field) {
    final Project project = field.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiMethod getter = GenerateMembersUtil.generateSimpleGetterPrototype(field);

    final PsiType wrappedType = JavaFxPsiUtil.getWrappedPropertyType(field, project, JavaFxCommonClassNames.ourReadOnlyMap);

    final PsiTypeElement returnTypeElement = getter.getReturnTypeElement();
    LOG.assertTrue(returnTypeElement != null);
    returnTypeElement.replace(factory.createTypeElement(wrappedType));

    final PsiCodeBlock getterBody = getter.getBody();
    LOG.assertTrue(getterBody != null);
    getterBody.getStatements()[0].replace(factory.createStatementFromText("return " + field.getName() + ".get();", field));

    final PsiMethod propertyGetter = PropertyUtil.generateGetterPrototype(field);
    propertyGetter.setName(JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD) + "Property");
    return new PsiMethod[] {getter, GenerateMembersUtil.annotateOnOverrideImplement(field.getContainingClass(), propertyGetter)};
  }

  @Override
  public PsiMethod[] generateSetters(PsiField field) {
    final PsiMethod setter = GenerateMembersUtil.generateSimpleSetterPrototype(field);
    final Project project = field.getProject();

    final PsiType wrappedType = JavaFxPsiUtil.getWrappedPropertyType(field, project, JavaFxCommonClassNames.ourWritableMap);
    
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
    final PsiMethod specificGetter = psiClass
      .findMethodBySignature(JavaPsiFacade.getElementFactory(psiClass.getProject()).createMethod(getterName, PsiType.VOID), false);
    if (specificGetter != null) {
      final PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, propertyName, false, false);
      return getter == null ? new PsiMethod[] {specificGetter} : new PsiMethod[] {getter, specificGetter};
    }
    return super.findGetters(psiClass, propertyName);
  }

  @Override
  public String suggestGetterName(String propertyName) {
    return propertyName + "Property";
  }

  @Override
  public boolean isSimpleGetter(PsiMethod method, String oldPropertyName) {
    return method.getName().equals(suggestGetterName(oldPropertyName));
  }

  @Override
  public boolean isReadOnly(PsiField field) {
    return !InheritanceUtil.isInheritor(field.getType(), JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_WRITABLE_VALUE);
  }
}
