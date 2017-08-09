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
import com.intellij.execution.TestClassCollector;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

class TestDirectory extends TestPackage {
  public TestDirectory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    final String dirName = getConfiguration().getPersistentData().getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    final GlobalSearchScope globalSearchScope = file == null ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScopesCore.directoryScope(
      getConfiguration().getProject(), file, true);
    return new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return globalSearchScope;
      }

      @Override
      public Project getProject() {
        return getConfiguration().getProject();
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        final Module module = getConfiguration().getConfigurationModule().getModule();
        return module != null ? GlobalSearchScope.moduleWithLibrariesScope(module) : GlobalSearchScope.allScope(
          getConfiguration().getProject());
      }

      @Override
      public Module[] getModulesToCompile() {
        final Collection<Module> validModules = getConfiguration().getValidModules();
        return validModules.toArray(new Module[validModules.size()]);
      }
    };
  }

  @Nullable
  @Override
  protected Path getRootPath() {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(getConfiguration().getPersistentData().getDirName()));
    if (file == null) return null;
    Module dirModule = ModuleUtilCore.findModuleForFile(file, getConfiguration().getProject());
    return TestClassCollector.getRootPath(dirModule, true);
  }

  @Override
  protected boolean configureByModule(Module module) {
    return module != null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String dirName = getConfiguration().getPersistentData().getDirName();
    if (dirName == null || dirName.isEmpty()) {
      throw new RuntimeConfigurationError("Directory is not specified");
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new RuntimeConfigurationWarning("Directory \'" + dirName + "\' is not found");
    }
    final Module module = getConfiguration().getConfigurationModule().getModule();
    if (module == null) {
      throw new RuntimeConfigurationError("Module to choose classpath from is not specified");
    }
  }

  @Override
  protected GlobalSearchScope filterScope(JUnitConfiguration.Data data) throws CantRunException {
    return GlobalSearchScope.allScope(getConfiguration().getProject());
  }

  @Override
  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return "";
  }

  @Override
  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final String dirName = data.getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    final PsiDirectory directory = PsiManager.getInstance(getConfiguration().getProject()).findDirectory(file);
    if (directory == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    return null;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String dirName = data.getDirName();
    return dirName.isEmpty() ? ExecutionBundle.message("all.tests.scope.presentable.text") 
                             : ExecutionBundle.message("test.in.scope.presentable.text", StringUtil.getShortName(dirName, '/'));
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    if (JUnitConfiguration.TEST_DIRECTORY.equals(configuration.getPersistentData().TEST_OBJECT) && testDir != null) {
      if (Comparing.strEqual(FileUtil.toSystemIndependentName(configuration.getPersistentData().getDirName()),
                             testDir.getVirtualFile().getPath())) {
        return true;
      }
    }
    return false;
  }
}
