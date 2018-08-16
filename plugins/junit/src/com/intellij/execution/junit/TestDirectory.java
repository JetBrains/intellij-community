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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.TestClassCollector;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.execution.junit.JUnitStarter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

class TestDirectory extends TestPackage {
  public TestDirectory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    final String dirName = getConfiguration().getPersistentData().getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    final Project project = getConfiguration().getProject();
    final GlobalSearchScope globalSearchScope =
      file == null ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScopesCore.directoryScope(project, file, true);
    return new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return globalSearchScope;
      }

      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        final Module module = getConfiguration().getConfigurationModule().getModule();
        return module != null ? GlobalSearchScope.moduleWithLibrariesScope(module)
                              : GlobalSearchScope.allScope(project);
      }

      @Override
      public Module[] getModulesToCompile() {
        final Collection<Module> validModules = getConfiguration().getValidModules();
        return validModules.toArray(Module.EMPTY_ARRAY);
      }
    };
  }

  @Nullable
  @Override
  protected Path getRootPath() {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(getConfiguration().getPersistentData().getDirName()));
    if (file == null) return null;
    Module dirModule = ModuleUtilCore.findModuleForFile(file, getConfiguration().getProject());
    if (dirModule == null) return null;
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
      throw new RuntimeConfigurationError("Directory \'" + dirName + "\' is not found");
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
  public SearchForTestsTask createSearchingForTestsTask() {
    if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) {
      return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket) {
        private final THashSet<PsiClass> classes = new THashSet<>();
        @Override
        protected void search() throws ExecutionException {
          PsiDirectory directory = getDirectory(getConfiguration().getPersistentData());
          PsiPackage aPackage = JavaRuntimeConfigurationProducerBase.checkPackage(directory);
          if (aPackage != null) {
            final Module module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), getProject());
            if (module != null) {
              ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
              PsiDirectory[] directories = aPackage.getDirectories(module.getModuleScope(true));
              boolean foundTestSources = false;
              for (PsiDirectory dir : directories) {
                if (fileIndex.isInTestSourceContent(dir.getVirtualFile())) {
                  if (foundTestSources) {
                    collectClassesRecursively(directory, Condition.TRUE, classes);
                    break;
                  }
                  foundTestSources = true;
                }
              }
            }
          }
        }

        @Override
        protected void onFound() throws ExecutionException {
          String packageName = TestDirectory.super.getPackageName(getConfiguration().getPersistentData());
          try {
            Path rootPath = getRootPath();
            LOG.assertTrue(rootPath != null);
            JUnitStarter
              .printClassesList(Collections.singletonList("\u002B" + rootPath.toFile().getAbsolutePath()), packageName, "",
                                classes.isEmpty() ? packageName + "\\..*" : StringUtil.join(classes, aClass -> ClassUtil.getJVMClassName(aClass), "||"), 
                                myTempFile);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      };
    }

    return super.createSearchingForTestsTask();
  }

  @Override
  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return "";
  }


  @Override
  protected void collectClassesRecursively(TestClassFilter classFilter, Condition<PsiClass> acceptClassCondition, Set<PsiClass> classes) throws CantRunException {
    collectClassesRecursively(getDirectory(getConfiguration().getPersistentData()), acceptClassCondition, classes);
  }


  private static void collectClassesRecursively(PsiDirectory directory,
                                                Condition<PsiClass> acceptAsTest,
                                                Set<PsiClass> classes) {
    PsiDirectory[] subDirectories = ReadAction.compute(() -> directory.getSubdirectories());
    for (PsiDirectory subDirectory : subDirectories) {
      collectClassesRecursively(subDirectory, acceptAsTest, classes);
    }
    PsiFile[] files = ReadAction.compute(() -> directory.getFiles());
    for (PsiFile file : files) {
      if (file instanceof PsiClassOwner) {
        for (PsiClass aClass : ReadAction.compute(() -> ((PsiClassOwner)file).getClasses())) {
          collectInnerClasses(aClass, acceptAsTest, classes);
        }
      }
    }
  }
  

  @Override
  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final PsiDirectory directory = getDirectory(data);
    return ReadAction.compute(() -> JavaDirectoryService.getInstance().getPackageInSources(directory));
  }

  private PsiDirectory getDirectory(JUnitConfiguration.Data data) throws CantRunException {
    final String dirName = data.getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    final PsiDirectory directory = ReadAction.compute(() -> PsiManager.getInstance(getConfiguration().getProject()).findDirectory(file));
    if (directory == null) {
      throw new CantRunException("Directory \'" + dirName + "\' is not found");
    }
    return directory;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String dirName = data.getDirName();
    return dirName.isEmpty() ? ExecutionBundle.message("all.tests.scope.presentable.text") 
                             : ExecutionBundle.message("test.in.scope.presentable.text", StringUtil.getShortName(FileUtil.toSystemIndependentName(dirName), '/'));
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
