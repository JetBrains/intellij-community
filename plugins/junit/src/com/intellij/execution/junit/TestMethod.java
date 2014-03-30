/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import org.jetbrains.annotations.NotNull;

class TestMethod extends TestObject {
  public TestMethod(final Project project,
                    final JUnitConfiguration configuration,
                    ExecutionEnvironment environment) {
    super(project, configuration, environment);
  }

  protected void initialize() throws ExecutionException {
    defaultInitialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    RunConfigurationModule module = myConfiguration.getConfigurationModule();
    configureModule(getJavaParameters(), module, data.getMainClassName());
    addJUnit3Parameter(data, module.getProject());
    getJavaParameters().getProgramParametersList().add(data.getMainClassName() + "," + data.getMethodName());
  }

  protected void defaultInitialize() throws ExecutionException {
    super.initialize();
  }

  protected void addJUnit3Parameter(final JUnitConfiguration.Data data, Project project) {
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(project, data.getMainClassName(), GlobalSearchScope.allScope(project));
    LOG.assertTrue(psiClass != null);
    if (JUnitUtil.isJUnit4TestClass(psiClass)) {
      return;
    }
    final String methodName = data.getMethodName();
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
    for (PsiMethod method : methods) {
      if (JUnitUtil.isTestAnnotated(method)) {
        return;
      }
    }
    myJavaParameters.getProgramParametersList().add(JUnitStarter.JUNIT3_PARAMETER);
  }

  public String suggestActionName() {
    return ProgramRunnerUtil.shortenName(myConfiguration.getPersistentData().METHOD_NAME, 2) + "()";
  }

  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (!method.getName().equals(configuration.getPersistentData().getMethodName())) return null;
      if (!method.getContainingClass().equals(configuration.myClass.getPsiElement())) return null;
      class Listener extends RefactoringElementAdapter implements UndoRefactoringElementListener {
        public void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
          final boolean generatedName = configuration.isGeneratedName();
          configuration.getPersistentData().setTestMethod(PsiLocation.fromPsiElement((PsiMethod)newElement));
          if (generatedName) configuration.setGeneratedName();
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          final int methodIdx = oldQualifiedName.indexOf("#") + 1;
          if (methodIdx <= 0 || methodIdx >= oldQualifiedName.length()) return;
          final boolean generatedName = configuration.isGeneratedName();
          configuration.getPersistentData().METHOD_NAME = oldQualifiedName.substring(methodIdx);
          if (generatedName) configuration.setGeneratedName();
        }
      }
      return new Listener();
    }
    else {
      return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
    }
  }


  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    if (testMethod == null) return false;
    if (testClass == null) return false;
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    /*final PsiClass containingClass = testMethod.getContainingClass();
    if (testClass == null && (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) return false;

    if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Comparing.equal(testMethod.getName(), data.getMethodName());
    }*/
    return
      Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(testClass), data.getMainClassName()) &&
      Comparing.equal(testMethod.getName(), data.getMethodName());
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final JavaRunConfigurationModule configurationModule = myConfiguration.getConfigurationModule();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    final String testClass = data.getMainClassName();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(testClass, ExecutionBundle.message("no.test.class.specified.error.text"));

    final String methodName = data.getMethodName();
    if (methodName == null || methodName.trim().length() == 0) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("method.name.not.specified.error.message"));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(psiClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("test.method.doesnt.exist.error.message", methodName));
    }

    if (!AnnotationUtil.isAnnotated(psiClass, JUnitUtil.RUN_WITH, true) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!psiClass.isInheritor(testCaseClass, true)) {
          throw new RuntimeConfigurationError(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", testClass));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("junit.jar.not.found.in.module.class.path.error.message", configurationModule.getModuleName()));
      }
    }
  }
}
