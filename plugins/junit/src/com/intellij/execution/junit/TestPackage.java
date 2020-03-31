// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.util.Function;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TestPackage extends TestObject {
  protected static final Function<Location<?>, String> CLASS_NAME_FUNCTION = location -> {
    if (location instanceof MethodLocation) {
      PsiClass containingClass = ((MethodLocation)location).getContainingClass();
      if (containingClass == null) return null;
      return ClassUtil.getJVMClassName(containingClass) + "," + ((MethodLocation)location).getPsiElement().getName();
    }
    PsiElement psiElement = location.getPsiElement();
    return psiElement instanceof PsiClass ? ClassUtil.getJVMClassName((PsiClass)psiElement) : null;
  };

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
  public SearchForTestsTask createSearchingForTestsTask() throws ExecutionException {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Module module = getConfiguration().getConfigurationModule().getModule();
    final TestClassFilter classFilter = computeFilter(data);
    return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket) {
      private final Set<Location<?>> myClasses = new LinkedHashSet<>();
      @Override
      protected void search() {
        myClasses.clear();
        final SourceScope sourceScope = getSourceScope();
        if (sourceScope != null) {
          try {
            if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) {
              searchTests5(module, classFilter, myClasses);
            }
            else {
              searchTests(module, classFilter, myClasses);
            }
          }
          catch (CantRunException ignored) {}
        }
      }

      @Override
      protected void onFound() {
        try {
          String packageName = getPackageName(data);
          String filters = getFilters(myClasses, packageName);
          if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner()) && module != null && filterOutputByDirectoryForJunit5(myClasses)) {
            JUnitStarter.printClassesList(composeDirectoryFilter(module), packageName, "", filters, myTempFile);
          }
          else {
            addClassesListToJavaParameters(myClasses, CLASS_NAME_FUNCTION, packageName, createTempFiles(), getJavaParameters(), filters);
          }
        }
        catch (Exception ignored) {}
      }

      @Override
      protected boolean requiresSmartMode() {
        return TestPackage.this.requiresSmartMode();
      }
    };
  }

  @Nullable
  private TestClassFilter computeFilter(JUnitConfiguration.Data data) throws ExecutionException {
    final TestClassFilter classFilter;
    try {
      classFilter =
        DumbService.getInstance(getConfiguration().getProject()).computeWithAlternativeResolveEnabled(() -> getClassFilter(data));
      LOG.assertTrue(classFilter.getBase() != null);
      return classFilter;
    }
    catch (JUnitUtil.NoJUnitException e) {
      return null;
    }
    catch (IndexNotReadyException e) {
      throw new ExecutionException("Running tests is disabled during index update");
    }
  }

  protected boolean requiresSmartMode() {
    return !JUnitStarter.JUNIT5_PARAMETER.equals(getRunner());
  }

  protected boolean filterOutputByDirectoryForJunit5(final Set<Location<?>> classNames) {
    return getConfiguration().getTestSearchScope() == TestSearchScope.SINGLE_MODULE;
  }

  protected String getFilters(Set<Location<?>> foundClasses, String packageName) {
    return foundClasses.isEmpty() ? packageName.isEmpty() ? ".*" : packageName + "\\..*" : "";
  }

  protected void searchTests5(Module module, TestClassFilter classFilter, Set<Location<?>> classes) throws CantRunException { }

  protected void searchTests(Module module, TestClassFilter classFilter, Set<Location<?>> classes) throws CantRunException {
    if (Registry.is("junit4.search.4.tests.all.in.scope", true)) {
      Condition<PsiClass> acceptClassCondition = aClass -> ReadAction.compute(() -> aClass.isValid() && classFilter.isAccepted(aClass));
      collectClassesRecursively(classFilter, acceptClassCondition, classes);
    }
    else {
      LinkedHashSet<PsiClass> psiClasses = new LinkedHashSet<>();
      ConfigurationUtil.findAllTestClasses(classFilter, module, psiClasses);
      psiClasses.stream().map(PsiLocation::fromPsiElement).forEach(classes::add);
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
                                           Set<Location<?>> classes) throws CantRunException {
    PsiPackage aPackage = getPackage(getConfiguration().getPersistentData());
    if (aPackage != null) {
      GlobalSearchScope scope = GlobalSearchScope.projectScope(getConfiguration().getProject()).intersectWith(classFilter.getScope());
      collectClassesRecursively(aPackage, scope, acceptClassCondition, classes);
    }
  }

  private static void collectClassesRecursively(PsiPackage aPackage,
                                                GlobalSearchScope scope,
                                                Condition<? super PsiClass> acceptAsTest,
                                                Set<Location<?>> classes) {
    PsiPackage[] psiPackages = ReadAction.compute(() -> aPackage.getSubPackages(scope));
    for (PsiPackage psiPackage : psiPackages) {
      collectClassesRecursively(psiPackage, scope, acceptAsTest, classes);
    }
    PsiClass[] psiClasses = ReadAction.compute(() -> aPackage.getClasses(scope));
    for (PsiClass aClass : psiClasses) {
      collectInnerClasses(aClass, acceptAsTest, classes);
    }
  }

  protected static void collectInnerClasses(PsiClass aClass, Condition<? super PsiClass> acceptAsTest, Set<Location<?>> classes) {
    if (Registry.is("junit4.accept.inner.classes", true)) {
      classes.addAll(ReadAction.compute(() -> JBTreeTraverser.of(PsiClass::getInnerClasses)
        .withRoot(aClass).filter(acceptAsTest).map(psiClass -> PsiLocation.fromPsiElement(psiClass))
        .toList()));
    }
    else if (acceptAsTest.value(aClass)) {
      classes.add(PsiLocation.fromPsiElement(aClass));
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
  protected void collectPackagesToOpen(List<String> options) {
    try {
      SourceScope sourceScope = getSourceScope();
      if (sourceScope != null) {
        collectSubPackages(options,
                           getPackage(getConfiguration().getPersistentData()),
                           sourceScope.getGlobalSearchScope());
      }
    }
    catch (CantRunException ignored) {
    }
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
    return ReadAction.compute(() -> PackageScope.packageScope(getPackage(data), true));
  }

  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final String packageName = data.getPackageName();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    return aPackage;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return data.getPackageName().trim().length() > 0
           ? ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName())
           : JUnitBundle.message("all.tests.scope.presentable.text");
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return element instanceof PsiPackage ? RefactoringListeners.getListener((PsiPackage)element, configuration.myPackage) : null;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return testPackage != null && Objects.equals(testPackage.getQualifiedName(), configuration.getPersistentData().getPackageName());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String packageName = getConfiguration().getPersistentData().getPackageName();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
    if (aPackage == null) {
      throw new RuntimeConfigurationWarning(JUnitBundle.message("package.does.not.exist.error.message", packageName));
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