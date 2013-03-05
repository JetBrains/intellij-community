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

import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;

import java.util.Map;

/**
 * User: anna
 * Date: 3/4/13
 */
public class JavaFxGetterSetterPrototypeProvider extends GetterSetterPrototypeProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxGetterSetterPrototypeProvider.class.getName());

  @Override
  public boolean accept(PsiField field) {
    return InheritanceUtil.isInheritor(field.getType(), JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE);
  }

  @Override
  public PsiMethod[] generateGetters(PsiField field) {
    final Project project = field.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiMethod getter = PropertyUtil.generateGetterPrototype(field);

    final PsiType wrappedType = getWrappedType(field, project, JavaFxCommonClassNames.ourReadOnlyMap);

    final PsiTypeElement returnTypeElement = getter.getReturnTypeElement();
    LOG.assertTrue(returnTypeElement != null);
    returnTypeElement.replace(factory.createTypeElement(wrappedType));

    final PsiCodeBlock getterBody = getter.getBody();
    LOG.assertTrue(getterBody != null);
    getterBody.getStatements()[0].replace(factory.createStatementFromText("return " + field.getName() + ".get();", field));

    final PsiMethod propertyGetter = PropertyUtil.generateGetterPrototype(field);
    propertyGetter.setName(JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD) + "Property");
    return new PsiMethod[] {getter, propertyGetter};
  }

  @Override
  public PsiMethod[] generateSetters(PsiField field) {
    final PsiMethod setter = PropertyUtil.generateSetterPrototype(field);
    final Project project = field.getProject();

    final PsiType wrappedType = getWrappedType(field, project, JavaFxCommonClassNames.ourWritableMap);
    
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
  public boolean isReadOnly(PsiField field) {
    return !InheritanceUtil.isInheritor(field.getType(), JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_WRITABLE_VALUE);
  }

  private static PsiType getWrappedType(PsiField field, Project project, final Map<String, PsiType> typeMap) {
    PsiType substitute = null;
    final PsiType fieldType = field.getType();
    for (String typeName : typeMap.keySet()) {
      if (InheritanceUtil.isInheritor(fieldType, typeName)) {
        substitute = typeMap.get(typeName);
        break;
      }
    }
    if (substitute == null) {
      final PsiClass aClass = JavaPsiFacade.getInstance(project)
        .findClass(JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE, GlobalSearchScope.allScope(project));
      LOG.assertTrue(aClass != null);
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(fieldType);
      final PsiClass fieldClass = resolveResult.getElement();
      LOG.assertTrue(fieldClass != null);
      final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, fieldClass, resolveResult.getSubstitutor());
      final PsiMethod[] values = aClass.findMethodsByName("getValue", false);
      substitute = substitutor.substitute(values[0].getReturnType());
    }
    return substitute;
  }
}
