// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.junit2.info.NestedClassLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentUtil;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
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
      return ((MethodLocation)location).getContainingClassJVMClassName() + "," + ((MethodLocation)location).getPsiElement().getName();
    }
    if (location instanceof NestedClassLocation) {
      PsiClass containingClass = ((NestedClassLocation)location).getContainingClass();
      if (containingClass == null) return null;
      return ClassUtil.getJVMClassName(containingClass) + "$" + ((NestedClassLocation)location).getPsiElement().getName();
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

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask() throws ExecutionException {
    return createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
  }

  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask(@NotNull TargetEnvironment remoteEnvironment) throws ExecutionException {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Module module = getConfiguration().getConfigurationModule().getModule();
    return new SearchForTestsTask(getConfiguration().getProject(), getServerSocket()) {
      private boolean myShouldExecuteFinishMethod = true;
      private final Set<Location<?>> myClasses = new LinkedHashSet<>();
      @Override
      protected void search() throws ExecutionException {
        myClasses.clear();
        final SourceScope sourceScope = getSourceScope();
        if (sourceScope != null) {
          if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) {
            searchTests5(module, myClasses);
          }
          else {
            final TestClassFilter classFilter = computeFilter(data);
            if (classFilter != null) {
              searchTests(module, classFilter, myClasses);
            }
          }
        }
      }

      @Override
      protected void onFound() {
        try {
          String packageName = getPackageName(data);
          String filters = getFilters(myClasses, packageName);
          if (JUnitStarter.JUNIT5_PARAMETER.equals(getRunner()) && module != null && filterOutputByDirectoryForJunit5(myClasses)) {
            JUnitStarter.printClassesList(composeDirectoryFilter(getModuleWithTestsToFilter(module)), packageName, "", filters, myTempFile);
          }
          else {
            addClassesListToJavaParameters(myClasses, CLASS_NAME_FUNCTION, packageName, createTempFiles(), getJavaParameters(), filters);
          }
        }
        catch (Exception ignored) {
        }

        myShouldExecuteFinishMethod = !TargetEnvironmentUtil.reuploadRootFile(myTempFile, getTargetEnvironmentRequest(),
                                                                              remoteEnvironment, getTargetProgressIndicator(),
                                                                              () -> ApplicationManager.getApplication()
                                                                            .invokeLater(super::finish, myProject.getDisposed()));
      }

      @Override
      public void finish() {
        if (myShouldExecuteFinishMethod) {
          super.finish();
        }
      }

      @Override
      protected boolean requiresSmartMode() {
        return TestPackage.this.requiresSmartMode();
      }
    };
  }

  protected Module getModuleWithTestsToFilter(Module module) {
    return module;
  }

  @Nullable
  private TestClassFilter computeFilter(JUnitConfiguration.Data data) throws CantRunException {
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
      throw new CantRunException(JUnitBundle.message("running.tests.disabled.during.index.update.error.message"));
    }
  }

  protected boolean requiresSmartMode() {
    return !JUnitStarter.JUNIT5_PARAMETER.equals(getRunner());
  }

  protected boolean filterOutputByDirectoryForJunit5(final Set<Location<?>> classNames) {
    return getConfiguration().getTestSearchScope() == TestSearchScope.SINGLE_MODULE;
  }

  protected @NlsSafe String getFilters(Set<Location<?>> foundClasses, @NlsSafe String packageName) {
    return "";
  }

  protected void searchTests5(Module module, Set<Location<?>> classes) throws CantRunException { }

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
  protected @NlsSafe String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return data.getPackageName();
  }

  protected void collectClassesRecursively(TestClassFilter classFilter,
                                           Condition<? super PsiClass> acceptClassCondition,
                                           Set<Location<?>> classes) throws CantRunException {
    PsiPackage aPackage = getPackage();
    GlobalSearchScope scope = GlobalSearchScope.projectScope(getConfiguration().getProject()).intersectWith(classFilter.getScope());
    collectClassesRecursively(aPackage, scope, acceptClassCondition, classes);
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
                           getPackage(),
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
    return ReadAction.compute(() -> PackageScope.packageScope(getPackage(), true));
  }

  @NotNull
  protected PsiPackage getPackage() throws CantRunException {
    final String packageName = getConfiguration().getPersistentData().getPackageName();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    return aPackage;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return data.getPackageName().trim().length() > 0
           ? ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName())
           : TestRunnerBundle.message("all.tests.scope.presentable.text");
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element) {
    return element instanceof PsiPackage ? RefactoringListeners.getListener((PsiPackage)element, getConfiguration().myPackage) : null;
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