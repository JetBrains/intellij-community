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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Predicate;

public class TestPackage extends TestObject {

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
        final SourceScope sourceScope = getSourceScope();
        final Module module = getConfiguration().getConfigurationModule().getModule();
        if (sourceScope != null && !ReadAction.compute(() -> isJUnit5(module, sourceScope, myProject))) {
          DumbService instance = DumbService.getInstance(myProject);
          try {
            instance.setAlternativeResolveEnabled(true);
            final TestClassFilter classFilter = getClassFilter(data);
            LOG.assertTrue(classFilter.getBase() != null);
            long start = System.currentTimeMillis();
            if (Registry.is("junit4.search.4.tests.in.classpath", false)) {
              String packageName = getPackageName(data);
              String[] classNames = TestClassCollector.collectClassFQNames(packageName, getConfiguration(), TestPackage::createPredicate);
              PsiManager manager = PsiManager.getInstance(myProject);
              Arrays.stream(classNames)
                .filter(className -> acceptClassName(className)) //check patterns
                .map(name -> ReadAction.compute(() -> ClassUtil.findPsiClass(manager, name, null, true, classFilter.getScope())))
                .filter(aClass -> aClass != null)
                .forEach(myClasses::add);
              LOG.info("Found tests in " + (System.currentTimeMillis() - start));
            }
            else {
              ConfigurationUtil.findAllTestClasses(classFilter, module, myClasses);
            }
          }
          catch (CantRunException ignored) {}
          finally {
            instance.setAlternativeResolveEnabled(false);
          }
        }
      }

      @Override
      protected void onFound() {

        try {
          addClassesListToJavaParameters(myClasses,
                                         psiClass -> psiClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(psiClass) : null, getPackageName(data), createTempFiles(), getJavaParameters());
        }
        catch (ExecutionException ignored) {}
      }
    };
  }

  protected boolean acceptClassName(String className) {
    return true;
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
    final SourceScope sourceScope = data.getScope().getSourceScope(getConfiguration());
    if (sourceScope == null || !isJUnit5(getConfiguration().getConfigurationModule().getModule(), sourceScope, project)) { //check for junit 5
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

  private static Predicate<Class<?>> createPredicate(ClassLoader classLoader) {

    try {
      Class<?> testCaseClass = Class.forName("junit.framework.TestCase", true, classLoader);

      @SuppressWarnings("unchecked")
      Class<? extends Annotation> runWithClass = (Class<? extends Annotation>)Class.forName("org.junit.runner.RunWith", true, classLoader);

      @SuppressWarnings("unchecked")
      Class<? extends Annotation> testClass = (Class<? extends Annotation>)Class.forName("org.junit.Test", true, classLoader);

      return aClass -> {
        //junit 3
        if (testCaseClass.isAssignableFrom(aClass)) {
          return hasSingleConstructor(aClass);
        }
        else {
          //annotation
          if (aClass.isAnnotationPresent(runWithClass)) {
            return true;
          }
          else {
            //junit 4 & suite
            for (Method method : aClass.getMethods()) {
              if (Modifier.isStatic(method.getModifiers()) && "suite".equals(method.getName()) ||
                  method.isAnnotationPresent(testClass)) {
                return hasSingleConstructor(aClass);
              }
            }
          }
        }
        return false;
      };
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      return aClass -> false;
    }
  }

  private static boolean hasSingleConstructor(Class<?> aClass) {
    Constructor<?>[] constructors = aClass.getConstructors();
    return constructors.length == 1 && constructors[0].getParameterTypes().length == 0;
  }
}
