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
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;

public class TestPackage extends TestObject {
  private BackgroundableProcessIndicator mySearchForTestsIndicator;

  public TestPackage(final Project project,
                     final JUnitConfiguration configuration,
                     RunnerSettings runnerSettings,
                     ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
  }


  public SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    return data.getScope().getSourceScope(myConfiguration);
  }

  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    try {
      return super.execute(executor, runner);
    }
    catch (ExecutionException e) {
      if (mySearchForTestsIndicator != null && !mySearchForTestsIndicator.isCanceled()) {
        mySearchForTestsIndicator.cancel(); //ensure that search for tests stops anyway
      }
      throw e;
    }
  }

  protected void initialize() throws ExecutionException {
    super.initialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    final TestClassFilter filter = getClassFilter(data);
    final ExecutionException[] exception = new ExecutionException[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
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

    try {
      myTempFile = File.createTempFile("idea_junit", ".tmp");
      myTempFile.deleteOnExit();
      myJavaParameters.getProgramParametersList().add("@" + myTempFile.getAbsolutePath());
    }
    catch (IOException e) {
      LOG.error(e);
    }

    try {
      final ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getByName(null));
      myJavaParameters.getProgramParametersList().add("-socket" + serverSocket.getLocalPort());
      findTestsWithProgress(new FindCallback() {
        public void found(@NotNull final Collection<PsiClass> classes, final boolean isJunit4) {
          addClassesListToJavaParameters(classes, new Function<PsiElement, String>() {
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
          }, data.getPackageName(), false, isJunit4);
        }
      }, filter, serverSocket);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private TestClassFilter getClassFilter(final JUnitConfiguration.Data data) throws CantRunException {
    Module module = myConfiguration.getConfigurationModule().getModule();
    if (myConfiguration.getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT){
      module = null;
    }
    final TestClassFilter classFilter = TestClassFilter.create(getSourceScope(), module);
    return classFilter.intersectionWith(filterScope(data));
  }

  protected GlobalSearchScope filterScope(final JUnitConfiguration.Data data) throws CantRunException {
    final Project project = myConfiguration.getProject();
    final String packageName = data.getPackageName();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    return PackageScope.packageScope(aPackage, true);
  }

  public String suggestActionName() {
    final String configurationName = myConfiguration.getName();
    if (!myConfiguration.isGeneratedName()) {
      return "'" + configurationName + "'";
    }
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    if (data.getPackageName().trim().length() > 0) {
      return ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName());
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    if (!(element instanceof PsiPackage)) return null;
    return RefactoringListeners.getListener((PsiPackage)element, configuration.myPackage);
  }

  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage) {
    return testPackage != null
           && Comparing.equal(testPackage.getQualifiedName(), configuration.getPersistentData().getPackageName());
  }

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

  private void findTestsWithProgress(final FindCallback callback, final TestClassFilter classFilter, final ServerSocket serverSocket) {
    if (isSyncSearch()) {
      THashSet<PsiClass> classes = new THashSet<PsiClass>();
      boolean isJUnit4 = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      callback.found(classes, isJUnit4);
      return;
    }

    final THashSet<PsiClass> classes = new THashSet<PsiClass>();
    final boolean[] isJunit4 = new boolean[1];
    final Task.Backgroundable task =
      new Task.Backgroundable(classFilter.getProject(), ExecutionBundle.message("seaching.test.progress.title"), true) {
        private Socket mySocket;

        
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            mySocket = serverSocket.accept();
          }
          catch (IOException e) {
            LOG.info(e);
          }
          isJunit4[0] = ConfigurationUtil.findAllTestClasses(classFilter, classes);
        }

        @Override
        public void onSuccess() {
          callback.found(classes, isJunit4[0]);
          connect();
        }

        @Override
        public void onCancel() {
          connect();
        }

        @Override
        public DumbModeAction getDumbModeAction() {
          return DumbModeAction.WAIT;
        }

        private void connect() {
          DataOutputStream os = null;
          try {
            os = new DataOutputStream(mySocket.getOutputStream());
            os.writeBoolean(true);
          }
          catch (Throwable e) {
            LOG.info(e);
          }
          finally {
            try {
              if (os != null) os.close();
            }
            catch (Throwable e) {
              LOG.info(e);
            }

            try {
              serverSocket.close();
            }
            catch (Throwable e) {
              LOG.info(e);
            }
          }
        }
      };
    mySearchForTestsIndicator = new BackgroundableProcessIndicator(task) {
      @Override
      public void cancel() {
        try {//ensure that serverSocket.accept was interrupted
          if (!serverSocket.isClosed()) {
            new Socket(InetAddress.getLocalHost(), serverSocket.getLocalPort());
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
        super.cancel();
      }
    };
    ProgressManagerImpl.runProcessWithProgressAsynchronously(task, mySearchForTestsIndicator);
  }

  private static boolean isSyncSearch() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public interface FindCallback {
    /**
     * Invoked in dispatch thread
     */
    void found(@NotNull Collection<PsiClass> classes, final boolean isJunit4);
  }
}
