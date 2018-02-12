/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.action;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;

import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 2/10/2016
 */
public class GradleRerunFailedTestsAction extends JavaRerunFailedTestsAction {
  public GradleRerunFailedTestsAction(GradleTestsExecutionConsole consoleView) {
    super(consoleView.getConsole(), consoleView.getProperties());
  }

  @Nullable
  @Override
  protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
    ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)myConsoleProperties.getConfiguration();
    final List<AbstractTestProxy> failedTests = getFailedTests(configuration.getProject());
    return new MyRunProfile(configuration) {
      @Nullable
      @Override
      public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment)
        throws ExecutionException {
        ExternalSystemRunConfiguration runProfile = ((ExternalSystemRunConfiguration)getPeer()).clone();
        Project project = runProfile.getProject();

        Set<String> scriptParameters = ContainerUtil.newLinkedHashSet();
        Set<String> tasksToRun = ContainerUtil.newLinkedHashSet();
        boolean useResolvedTasks = true;
        for (AbstractTestProxy test : failedTests) {
          if (test instanceof GradleSMTestProxy) {
            String testName = test.getName();
            String className = ((GradleSMTestProxy)test).getClassName();
            scriptParameters.add(TestMethodGradleConfigurationProducer.createTestFilter(className, testName));

            if(!useResolvedTasks) continue;

            if(className == null) {
              useResolvedTasks = false;
              continue;
            }

            final PsiClass psiClass =
              JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project));

            if (psiClass == null)  {
              useResolvedTasks = false;
              continue;
            }
            final PsiFile psiFile = psiClass.getContainingFile();
            if (psiFile == null)  {
              useResolvedTasks = false;
              continue;
            }

            final Module moduleForFile = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(psiFile.getVirtualFile());
            if(moduleForFile == null){
              useResolvedTasks = false;
              continue;
            }
            ContainerUtil.addAllNotNull(tasksToRun, GradleTestRunConfigurationProducer.getTasksToRun(moduleForFile));
          }
        }
        runProfile.getSettings().setScriptParameters(StringUtil.join(scriptParameters, " "));

        if(useResolvedTasks && !tasksToRun.isEmpty()) {
          runProfile.getSettings().setTaskNames(ContainerUtil.newArrayList(tasksToRun));
        }
        return runProfile.getState(executor, environment);
      }
    };
  }
}
