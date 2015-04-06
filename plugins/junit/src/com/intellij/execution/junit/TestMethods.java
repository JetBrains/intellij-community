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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;

public class TestMethods extends TestMethod {
  private static final Logger LOG = Logger.getInstance(TestMethods.class);

  private final Collection<AbstractTestProxy> myFailedTests;

  public TestMethods(@NotNull JUnitConfiguration configuration,
                     @NotNull ExecutionEnvironment environment,
                     @NotNull Collection<AbstractTestProxy> failedTests) {
    super(configuration, environment);

    myFailedTests = failedTests;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createDefaultJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    RunConfigurationModule module = getConfiguration().getConfigurationModule();
    final Project project = module.getProject();
    final LinkedHashSet<TestInfo> methods = new LinkedHashSet<TestInfo>();
    final GlobalSearchScope searchScope = getConfiguration().getConfigurationModule().getSearchScope();
    for (AbstractTestProxy failedTest : myFailedTests) {
      Location location = failedTest.getLocation(project, searchScope);
      if (location instanceof PsiMemberParameterizedLocation) {
        final PsiElement element = location.getPsiElement();
        if (element instanceof PsiMethod) {
          location = MethodLocation.elementInClass(((PsiMethod)element),
                                                   ((PsiMemberParameterizedLocation)location).getContainingClass());
        }
      }
      if (!(location instanceof MethodLocation)) continue;
      PsiElement psiElement = location.getPsiElement();
      LOG.assertTrue(psiElement instanceof PsiMethod);
      methods.add(((TestProxy)failedTest).getInfo());
    }
    addClassesListToJavaParameters(methods, new Function<TestInfo, String>() {
      @Override
      public String fun(TestInfo testInfo) {
        if (testInfo != null) {
          final MethodLocation location = (MethodLocation)testInfo.getLocation(project, searchScope);
          LOG.assertTrue(location != null);
          return JavaExecutionUtil.getRuntimeQualifiedName(location.getContainingClass()) + "," + testInfo.getName();
        }
        return null;
      }
    }, data.getPackageName(), true, javaParameters);

    return javaParameters;
  }

  @Override
  public String suggestActionName() {
    return ActionsBundle.message("action.RerunFailedTests.text");
  }
}
