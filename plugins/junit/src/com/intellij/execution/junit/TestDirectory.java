/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Collection;
import java.util.List;

/**
* User: anna
* Date: 4/21/11
*/
class TestDirectory extends TestPackage {
  private final Project myProject;

  public TestDirectory(Project project,
                       JUnitConfiguration configuration,
                       RunnerSettings runnerSettings,
                       ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
    myProject = project;
  }

  @Override
  public SourceScope getSourceScope() {
    final String dirName = myConfiguration.getPersistentData().getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    final GlobalSearchScope globalSearchScope = file == null ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScope.directoryScope(myProject, file, true);
    return new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return globalSearchScope;
      }

      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        final Module module = myConfiguration.getConfigurationModule().getModule();
        LOG.assertTrue(module != null);
        return GlobalSearchScope.moduleWithLibrariesScope(module);
      }

      @Override
      public Module[] getModulesToCompile() {
        final Collection<Module> validModules = myConfiguration.getValidModules();
        return validModules.toArray(new Module[validModules.size()]);
      }
    };
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (myConfiguration.isAlternativeJrePathEnabled()) {
      if (myConfiguration.getAlternativeJrePath() == null ||
          myConfiguration.getAlternativeJrePath().length() == 0 ||
          !JavaSdk.checkForJre(myConfiguration.getAlternativeJrePath())) {
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.mesage", myConfiguration.getAlternativeJrePath()));
      }
    }
    final String dirName = myConfiguration.getPersistentData().getDirName();
    if (dirName == null || dirName.isEmpty()) {
      throw new RuntimeConfigurationWarning("Directory is not specified");
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new RuntimeConfigurationWarning("Directory \'" + dirName + "\' is not found");
    }
    final Module module = myConfiguration.getConfigurationModule().getModule();
    if (module == null) {
      throw new RuntimeConfigurationWarning("Module to choose classpath from is not specified");
    }
  }

  @Override
  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final String dirName = data.getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(file);
    if (directory == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      throw new CantRunException("Package not found in directory");
    }
    return aPackage;
  }
}
