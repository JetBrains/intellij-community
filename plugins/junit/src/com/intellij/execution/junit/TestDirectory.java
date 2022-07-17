// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

class TestDirectory extends TestPackage {
  TestDirectory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
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

  @Override
  protected Module getModuleWithTestsToFilter(Module module) {
    try {
      PsiDirectory directory = getDirectory(getConfiguration().getPersistentData());
      return ModuleUtilCore.findModuleForPsiElement(directory);
    }
    catch (CantRunException e) {
      return module;
    }
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
      throw new RuntimeConfigurationError(JUnitBundle.message("directory.is.not.specified.error.message"));
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new RuntimeConfigurationError(JUnitBundle.message("directory.0.is.not.found.error.message", dirName));
    }
    final Module module = getConfiguration().getConfigurationModule().getModule();
    if (module == null) {
      throw new RuntimeConfigurationError(JUnitBundle.message("module.to.choose.classpath.not.specified.error.message"));
    }
  }

  @Override
  protected GlobalSearchScope filterScope(JUnitConfiguration.Data data) {
    return GlobalSearchScope.allScope(getConfiguration().getProject());
  }

  @Override
  protected boolean requiresSmartMode() {
    return true;
  }

  @Override
  protected void searchTests5(Module module, Set<Location<?>> classes) throws CantRunException {
    if (module != null) {
      PsiDirectory directory = getDirectory(getConfiguration().getPersistentData());
      PsiPackage aPackage = AbstractJavaTestConfigurationProducer.checkPackage(directory);
      if (aPackage != null) {
        GlobalSearchScope projectScope = GlobalSearchScopesCore.projectTestScope(getConfiguration().getProject());
        PsiDirectory[] directories = aPackage.getDirectories(module.getModuleScope(true).intersectWith(projectScope));
        if (directories.length > 1) {  // need to enumerate classes in one of multiple test source roots
          collectClassesRecursively(directory, Conditions.alwaysTrue(), classes);
        }
      }
    }
  }

  @Override
  protected boolean filterOutputByDirectoryForJunit5(Set<Location<?>> classNames) {
    return true;
  }

  @Override
  protected String getFilters(Set<Location<?>> foundClasses, String packageName) {
    return foundClasses.isEmpty()
           ? super.getFilters(foundClasses, packageName)
           : StringUtil.join(foundClasses, CLASS_NAME_FUNCTION, "||");
  }

  @Override
  protected void collectClassesRecursively(TestClassFilter classFilter,
                                           Condition<? super PsiClass> acceptClassCondition,
                                           Set<Location<?>> classes) throws CantRunException {
    collectClassesRecursively(getDirectory(getConfiguration().getPersistentData()), acceptClassCondition, classes);
  }


  private static void collectClassesRecursively(PsiDirectory directory,
                                                Condition<? super PsiClass> acceptAsTest,
                                                Set<Location<?>> classes) {
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

  @NotNull
  @Override
  protected PsiPackage getPackage() throws CantRunException {
    final PsiDirectory directory = getDirectory(getConfiguration().getPersistentData());
    PsiPackage aPackage = ReadAction.compute(() -> JavaDirectoryService.getInstance().getPackageInSources(directory));
    if (aPackage == null) throw CantRunException.packageNotFound(directory.getName());
    return aPackage;
  }

  @Override
  protected @NotNull String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return getPackage().getQualifiedName();
  }

  private PsiDirectory getDirectory(JUnitConfiguration.Data data) throws CantRunException {
    final String dirName = data.getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new CantRunException(JUnitBundle.message("directory.not.found.error.message", dirName));
    }
    final PsiDirectory directory = ReadAction.compute(() -> PsiManager.getInstance(getConfiguration().getProject()).findDirectory(file));
    if (directory == null) {
      throw new CantRunException(JUnitBundle.message("directory.not.found.error.message", dirName));
    }
    return directory;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String dirName = data.getDirName();
    return dirName.isEmpty() ? TestRunnerBundle.message("all.tests.scope.presentable.text")
                             : ExecutionBundle.message("test.in.scope.presentable.text", StringUtil.getShortName(FileUtil.toSystemIndependentName(dirName), '/'));
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return JUnitConfiguration.TEST_DIRECTORY.equals(configuration.getPersistentData().TEST_OBJECT) &&
           testDir != null &&
           VfsUtilCore.pathEqualsTo(testDir.getVirtualFile(), configuration.getPersistentData().getDirName());
  }
}