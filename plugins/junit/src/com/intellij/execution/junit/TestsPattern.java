/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 11-Jun-2010
 */
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class TestsPattern extends TestPackage {
  public TestsPattern(final Project project,
                      final JUnitConfiguration configuration,
                      RunnerSettings runnerSettings,
                      ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
  }

  @Override
  protected GlobalSearchScope filterScope(JUnitConfiguration.Data data) throws CantRunException {
    final Pattern pattern = Pattern.compile(data.getPattern());

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myConfiguration.getProject());
    final PsiManager manager = PsiManager.getInstance(myConfiguration.getProject());
    return new GlobalSearchScope() {
      @Override
      public boolean contains(VirtualFile file) {
        if (!file.isDirectory()) {
          final PsiFile psiFile = manager.findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            if (pattern.matcher(((PsiClassOwner)psiFile).getPackageName()).matches()) return true;
          }
        }

        return false;
      }

      @Override
      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean isSearchInLibraries() {
        return false;
      }
    };
  }

  @Override
  public String suggestActionName() {
    final String configurationName = myConfiguration.getName();
    if (!myConfiguration.isGeneratedName()) {
    }
    return "'" + configurationName + "'"; //todo
  }

  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration) {
    return null;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage) {
    return false;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    if (data.getPattern().trim().length() == 0) {
      throw new RuntimeConfigurationWarning("No pattern selected");
    }


  }
}