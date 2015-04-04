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
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collection;

public class TestPackage extends TestObject {
  protected BackgroundableProcessIndicator mySearchForTestsIndicator;
  protected ServerSocket myServerSocket;
  private boolean myFoundTests = true;

  public TestPackage(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  public SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    return data.getScope().getSourceScope(myConfiguration);
  }

  @Override
  protected JUnitProcessHandler createHandler(Executor executor) throws ExecutionException {
    final JUnitProcessHandler handler = super.createHandler(executor);
    final SearchForTestsTask[] tasks = new SearchForTestsTask[1];
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(ProcessEvent event) {
        super.startNotified(event);
        tasks[0] = (SearchForTestsTask)findTests();
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        handler.removeProcessListener(this);
        if (mySearchForTestsIndicator != null && !mySearchForTestsIndicator.isCanceled()) {
          if (tasks[0] != null) {
            tasks[0].finish();
          }
        }
      }
    });
    return handler;
  }

  public Task findTests() {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    final TestClassFilter filter;
    try {
      filter = getClassFilter(data);
    }
    catch (CantRunException ignored) {
      //should not happen
      return null;
    }

    return findTestsWithProgress(new Consumer<Collection<PsiClass>>() {
      @Override
      public void consume(Collection<PsiClass> classes) {
        try {
          addClassesListToJavaParameters(classes, new Function<PsiElement, String>() {
            @Override
            @Nullable
            public String fun(PsiElement element) {
              if (element instanceof PsiClass) {
                return JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)element);
              }
              else if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod)element;
                return JavaExecutionUtil.getRuntimeQualifiedName(method.getContainingClass()) + "," + method.getName();
              }
              else {
                return null;
              }
            }
          }, getPackageName(data), false);
        }
        catch (CantRunException ignored) {
          //can't be here
        }
      }
    }, filter);
  }

  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return getPackage(data).getQualifiedName();
  }

  @Override
  protected void initialize() throws ExecutionException {
    super.initialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    getClassFilter(data);//check if junit found
    configureClasspath();

    try {
      createTempFiles();
    }
    catch (IOException e) {
      LOG.error(e);
    }

    try {
      myServerSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      myJavaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected void configureClasspath() throws ExecutionException {
    final ExecutionException[] exception = new ExecutionException[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        try {
          myConfiguration.configureClasspath(myJavaParameters);
        }
        catch (CantRunException e) {
          exception[0] = e;
        }
      }
    });
    if (exception[0] != null) {
      throw exception[0];
    }
  }

  protected TestClassFilter getClassFilter(final JUnitConfiguration.Data data) throws CantRunException {
    Module module = myConfiguration.getConfigurationModule().getModule();
    if (myConfiguration.getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT){
      module = null;
    }
    final TestClassFilter classFilter = TestClassFilter.create(getSourceScope(), module);
    return classFilter.intersectionWith(filterScope(data));
  }

  protected GlobalSearchScope filterScope(final JUnitConfiguration.Data data) throws CantRunException {
    final PsiPackage aPackage = getPackage(data);
    return PackageScope.packageScope(aPackage, true);
  }

  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final Project project = myConfiguration.getProject();
    final String packageName = data.getPackageName();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    return aPackage;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
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
    final String packageName = myConfiguration.getPersistentData().getPackageName();
    final PsiPackage aPackage =
      JavaPsiFacade.getInstance(myConfiguration.getProject()).findPackage(packageName);
    if (aPackage == null) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", packageName));
    }
    if (getSourceScope() == null) {
      myConfiguration.getConfigurationModule().checkForWarning();
    }
  }

  private SearchForTestsTask findTestsWithProgress(final Consumer<Collection<PsiClass>> callback, final TestClassFilter classFilter) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      THashSet<PsiClass> classes = new THashSet<PsiClass>();
      ConfigurationUtil.findAllTestClasses(classFilter, classes);
      callback.consume(classes);
      return null;
    }

    final THashSet<PsiClass> classes = new THashSet<PsiClass>();
    final SearchForTestsTask task =
      new SearchForTestsTask(classFilter.getProject(), myServerSocket) {
        @Override
        protected void search() {
          classes.clear();
          ConfigurationUtil.findAllTestClasses(classFilter, classes);
        }

        @Override
        protected void onFound() {
          myFoundTests = !classes.isEmpty();
          callback.consume(classes);
        }
      };
    mySearchForTestsIndicator = new BackgroundableProcessIndicator(task);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, mySearchForTestsIndicator);
    return task;
  }

  @Override
  protected void notifyByBalloon(JUnitRunningModel model, boolean started, final JUnitConsoleProperties consoleProperties) {
    if (myFoundTests || !ResetConfigurationModuleAdapter.tryWithAnotherModule(myConfiguration, consoleProperties.isDebug())) {
      super.notifyByBalloon(model, started, consoleProperties);
    }
  }
}
