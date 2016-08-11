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

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;

public class TestPackage extends TestObject {
  private boolean myFoundTests = true;

  public TestPackage(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return data.getScope().getSourceScope(getConfiguration());
  }

  @Override
  public SearchForTestsTask createSearchingForTestsTask() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();

    return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket) {
      private final THashSet<PsiClass> myClasses = new THashSet<>();
      @Override
      protected void search() {
        myClasses.clear();
        try {
          ConfigurationUtil.findAllTestClasses(getClassFilter(data), myClasses);
        }
        catch (CantRunException ignored) {}
      }

      @Override
      protected void onFound() {
        myFoundTests = !myClasses.isEmpty();

        try {
          addClassesListToJavaParameters(myClasses,
                                         psiClass -> psiClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(psiClass) : null, getPackageName(data), createTempFiles(), getJavaParameters());
        }
        catch (ExecutionException ignored) {}
      }
    };
  }

  protected boolean createTempFiles() {
    return false;
  }

  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return getPackage(data).getQualifiedName();
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Project project = getConfiguration().getProject();
    final DumbService dumbService = DumbService.getInstance(project);
    try {
      dumbService.setAlternativeResolveEnabled(true);
      final SourceScope sourceScope = data.getScope().getSourceScope(getConfiguration());
      if (sourceScope == null || !JUnitUtil.isJUnit5(sourceScope.getLibrariesScope(), project)) { //check for junit 5
        getClassFilter(data);//check if junit 4 found
      }
    }
    finally {
      dumbService.setAlternativeResolveEnabled(false);
    }
    createTempFiles(javaParameters);

    createServerSocket(javaParameters);
    return javaParameters;
  }

  @Override
  protected boolean configureByModule(Module module) {
    return super.configureByModule(module) && getConfiguration().getPersistentData().getScope() != TestSearchScope.WHOLE_PROJECT;
  }

  protected TestClassFilter getClassFilter(final JUnitConfiguration.Data data) throws CantRunException {
    Module module = getConfiguration().getConfigurationModule().getModule();
    if (getConfiguration().getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT){
      module = null;
    }
    final TestClassFilter classFilter = TestClassFilter.create(getSourceScope(), module);
    return classFilter.intersectionWith(filterScope(data));
  }

  protected GlobalSearchScope filterScope(final JUnitConfiguration.Data data) throws CantRunException {
    final Ref<CantRunException> ref = new Ref<>();
    final GlobalSearchScope aPackage = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        try {
          return PackageScope.packageScope(getPackage(data), true);
        }
        catch (CantRunException e) {
          ref.set(e);
          return null;
        }
      }
    });
    final CantRunException exception = ref.get();
    if (exception != null) throw exception;
    return aPackage;
  }

  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final Project project = getConfiguration().getProject();
    final String packageName = data.getPackageName();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    return aPackage;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    if (data.getPackageName().trim().length() > 0) {
      return ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName());
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    if (!(element instanceof PsiPackage)) return null;
    return RefactoringListeners.getListener((PsiPackage)element, configuration.myPackage);
  }

  @Override
  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return testPackage != null
           && Comparing.equal(testPackage.getQualifiedName(), configuration.getPersistentData().getPackageName());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String packageName = getConfiguration().getPersistentData().getPackageName();
    final PsiPackage aPackage =
      JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
    if (aPackage == null) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", packageName));
    }
    if (getSourceScope() == null) {
      getConfiguration().getConfigurationModule().checkForWarning();
    }
  }

  @TestOnly
  public File getWorkingDirsFile() {
    return myWorkingDirsFile;
  }
}
