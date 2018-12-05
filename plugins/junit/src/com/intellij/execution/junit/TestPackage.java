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
import com.intellij.execution.ConfigurationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class TestPackage extends TestObject {
  protected static final Function<PsiClass, String> CLASS_NAME_FUNCTION = psiClass -> psiClass != null ? ClassUtil.getJVMClassName(psiClass) : null;

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
    final Module module = getConfiguration().getConfigurationModule().getModule();
    return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket) {
      private final Set<PsiClass> myClasses = new LinkedHashSet<>();
      @Override
      protected void search() {
        myClasses.clear();
        final SourceScope sourceScope = getSourceScope();
        if (sourceScope != null) {
          try {
            final TestClassFilter classFilter = getClassFilter(data);
            LOG.assertTrue(classFilter.getBase() != null);
            searchTests(module, classFilter, myClasses);
          }
          catch (CantRunException ignored) {}
        }
      }

      @Override
      protected void onFound() {

        try {
          String packageName = getPackageName(data);
          if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner()) && intersectWithDirectory(myClasses) && module != null && inSingleModule()) {
            VirtualFile[] rootPaths = OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules().classes().getRoots();
            JUnitStarter.printClassesList(
              ContainerUtil.map(rootPaths, root -> "\u002B" + root.getPath()), packageName, "", getFilters(myClasses, packageName), myTempFile);
          }
          else {
            addClassesListToJavaParameters(myClasses, CLASS_NAME_FUNCTION, packageName, createTempFiles(), getJavaParameters(), getFilters(myClasses, packageName));
          }
        }
        catch (Exception ignored) {}
      }
    };
  }

  protected boolean intersectWithDirectory(final Set<PsiClass> classNames) {
    return classNames.isEmpty();
  }

  protected boolean inSingleModule() {
    return getConfiguration().getTestSearchScope() == TestSearchScope.SINGLE_MODULE;
  }

  protected String getFilters(Set<PsiClass> foundClassNames, String packageName) {
    return foundClassNames.isEmpty()
           ? packageName.isEmpty() ? ".*" : packageName + "\\..*"
           : "";
  }

  protected void searchTests(Module module, TestClassFilter classFilter, Set<PsiClass> classes) throws CantRunException {
    if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) return; //junit 5 process tests automatically
    if (Registry.is("junit4.search.4.tests.all.in.scope", true)) {
      Condition<PsiClass> acceptClassCondition = aClass -> ReadAction.compute(() -> aClass.isValid() && classFilter.isAccepted(aClass));
      collectClassesRecursively(classFilter, acceptClassCondition, classes);
    }
    else {
      ConfigurationUtil.findAllTestClasses(classFilter, module, classes);
    }
  }

  protected boolean createTempFiles() {
    return false;
  }

  @NotNull
  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    PsiPackage aPackage = getPackage(data);
    return aPackage != null ? aPackage.getQualifiedName() : "";
  }

  protected void collectClassesRecursively(TestClassFilter classFilter,
                                           Condition<? super PsiClass> acceptClassCondition,
                                           Set<? super PsiClass> classes) throws CantRunException {
    PsiPackage aPackage = getPackage(getConfiguration().getPersistentData());
    if (aPackage != null) {
      GlobalSearchScope scope = GlobalSearchScope.projectScope(getConfiguration().getProject()).intersectWith(classFilter.getScope());
      collectClassesRecursively(aPackage, scope, acceptClassCondition, classes);
    }
  }

  private static void collectClassesRecursively(PsiPackage aPackage,
                                                GlobalSearchScope scope,
                                                Condition<? super PsiClass> acceptAsTest,
                                                Set<? super PsiClass> classes) {
    PsiPackage[] psiPackages = ReadAction.compute(() -> aPackage.getSubPackages(scope));
    for (PsiPackage psiPackage : psiPackages) {
      collectClassesRecursively(psiPackage, scope, acceptAsTest, classes);
    }
    PsiClass[] psiClasses = ReadAction.compute(() -> aPackage.getClasses(scope));
    for (PsiClass aClass : psiClasses) {
      collectInnerClasses(aClass, acceptAsTest, classes);
    }
  }

  protected static void collectInnerClasses(PsiClass aClass, Condition<? super PsiClass> acceptAsTest, Set<? super PsiClass> classes) {
    if (Registry.is("junit4.accept.inner.classes", true)) {
      classes
        .addAll(ReadAction.compute(() -> JBTreeTraverser.of(PsiClass::getInnerClasses).withRoot(aClass).filter(acceptAsTest).toList()));
    }
    else if (acceptAsTest.value(aClass)) {
      classes.add(aClass);
    }
  }


  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Project project = getConfiguration().getProject();
    final SourceScope sourceScope = data.getScope().getSourceScope(getConfiguration());
    if (sourceScope == null || !JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) { //check for junit 5
      JUnitUtil.checkTestCase(sourceScope, project);
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
    final GlobalSearchScope aPackage = ReadAction.compute(() -> {
      try {
        return PackageScope.packageScope(getPackage(data), true);
      }
      catch (CantRunException e) {
        ref.set(e);
        return null;
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
