/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoverySearchHelper;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.FunctionUtil;

import java.util.Set;

abstract class JUnitTestDiscoveryRunnableState extends TestObject {
  public JUnitTestDiscoveryRunnableState(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  protected abstract String getChangeList();
  protected abstract Pair<String, String> getPosition();


  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getConfigurationModule().getModule() != null ? TestSearchScope.MODULE_WITH_DEPENDENCIES : TestSearchScope.WHOLE_PROJECT;
  }

  @Override
  protected boolean forkPerModule() {
    return getConfiguration().getConfigurationModule().getModule() == null;
  }

  @Override
  protected PsiElement retrievePsiElement(Object pattern) {
    if (pattern instanceof String) {
      final String className = StringUtil.getPackageName((String)pattern, ',');
      if (!pattern.equals(className)) {
        final Project project = getConfiguration().getProject();
        PsiManager manager = PsiManager.getInstance(project);
        final SourceScope sourceScope = getSourceScope();
        final GlobalSearchScope globalSearchScope = sourceScope != null ? sourceScope.getGlobalSearchScope()
                                                                        : GlobalSearchScope.projectScope(project);
        return ClassUtil.findPsiClass(manager, className, null, true, globalSearchScope);
      }
    }
    return null;
  }

  @Override
  public SearchForTestsTask createSearchingForTestsTask() {
    return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket) {

      private Set<String> myPatterns;

      @Override
      protected void search() {
        myPatterns = TestDiscoverySearchHelper.search(getProject(), getPosition(), getChangeList(), getConfiguration().getTestFrameworkId());
      }

      @Override
      protected void onFound() {
        if (myPatterns != null) {
          try {
            addClassesListToJavaParameters(myPatterns, FunctionUtil.id(), "", false, getJavaParameters());
          }
          catch (ExecutionException ignored) {
          }
        }
      }
    };
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    createTempFiles(javaParameters);

    createServerSocket(javaParameters);
    return javaParameters;
  }

  @Override
  public String suggestActionName() {
    return "";
  }

  @Override
  public RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration) {
    return null;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }
}
