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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfigurationModule;
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

import java.util.Collection;
import java.util.LinkedHashSet;

public class TestMethods extends TestMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestMethods");

  private final Collection<AbstractTestProxy> myFailedTests;

  public TestMethods(final Project project,
                     final JUnitConfiguration configuration,
                     ExecutionEnvironment environment,
                     Collection<AbstractTestProxy> failedTests) {
    super(project, configuration, environment);
    myFailedTests = failedTests;
  }

  protected void initialize() throws ExecutionException {
    defaultInitialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    RunConfigurationModule module = myConfiguration.getConfigurationModule();
    final Project project = module.getProject();
    final ExecutionException[] exception = new ExecutionException[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          myConfiguration.configureClasspath(myJavaParameters);
        }
        catch (CantRunException e) {
          exception[0] = e;
        }
      }
    });
    if (exception[0] != null) throw exception[0];
    final LinkedHashSet<TestInfo> methods = new LinkedHashSet<TestInfo>();
    final GlobalSearchScope searchScope = myConfiguration.getConfigurationModule().getSearchScope();
    for (AbstractTestProxy failedTest : myFailedTests) {
      Location location = failedTest.getLocation(project, searchScope);
      if (!(location instanceof MethodLocation)) continue;
      PsiElement psiElement = location.getPsiElement();
      LOG.assertTrue(psiElement instanceof PsiMethod);
      PsiMethod method = (PsiMethod)psiElement;
      methods.add(((TestProxy)failedTest).getInfo());
    }
    addClassesListToJavaParameters(methods, new Function<TestInfo, String>() {
      public String fun(TestInfo testInfo) {
        if (testInfo != null) {
          final MethodLocation location = (MethodLocation)testInfo.getLocation(project, searchScope);
          LOG.assertTrue(location != null);
          return JavaExecutionUtil.getRuntimeQualifiedName(location.getContainingClass()) + "," + testInfo.getName();
        }
        return null;
      }
    }, data.getPackageName(), true, false);

  }

  public String suggestActionName() {
    return ActionsBundle.message("action.RerunFailedTests.text");
  }
}
