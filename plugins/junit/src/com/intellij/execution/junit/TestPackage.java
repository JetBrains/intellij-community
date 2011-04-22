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
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TestPackage extends TestObject {
  private BackgroundableProcessIndicator mySearchForTestsIndicator;
  private ServerSocket myServerSocket;
  private boolean myFoundTests = true;

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
  protected JUnitProcessHandler createHandler() throws ExecutionException {
    final JUnitProcessHandler handler = super.createHandler();
    final MySearchForTestsTask[] tasks = new MySearchForTestsTask[1];
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(ProcessEvent event) {
        super.startNotified(event);
        final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
        final TestClassFilter filter;
        try {
          filter = getClassFilter(data);
        }
        catch (CantRunException e) {
          //should not happen
          return;
        }
        tasks[0] = findTestsWithProgress(new FindCallback() {
          public void found(@NotNull final Collection<PsiClass> classes, final boolean isJunit4) {
            try {
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
              }, getPackage(data).getQualifiedName(), false, isJunit4);
            }
            catch (CantRunException e) {
              //can't be here
            }
          }
        }, filter);
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        handler.removeProcessListener(this);
        if (mySearchForTestsIndicator != null && !mySearchForTestsIndicator.isCanceled()) {
          mySearchForTestsIndicator.cancel(); //ensure that search for tests stops anyway
          if (tasks[0] != null) {
            tasks[0].connect();
          }
        }
      }
    });
    return handler;
  }

  protected void initialize() throws ExecutionException {
    super.initialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    getClassFilter(data);//check if junit found
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
      myTempFile = FileUtil.createTempFile("idea_junit", ".tmp");
      myTempFile.deleteOnExit();
      myJavaParameters.getProgramParametersList().add("@" + myTempFile.getAbsolutePath());
    }
    catch (IOException e) {
      LOG.error(e);
    }

    try {
      myServerSocket = new ServerSocket(0, 0, InetAddress.getByName(null));
      myJavaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
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

  private MySearchForTestsTask findTestsWithProgress(final FindCallback callback, final TestClassFilter classFilter) {
    if (isSyncSearch()) {
      THashSet<PsiClass> classes = new THashSet<PsiClass>();
      boolean isJUnit4 = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      callback.found(classes, isJUnit4);
      return null;
    }

    final THashSet<PsiClass> classes = new THashSet<PsiClass>();
    final boolean[] isJunit4 = new boolean[1];
    final MySearchForTestsTask task =
      new MySearchForTestsTask(classFilter, isJunit4, classes, callback);
    mySearchForTestsIndicator = new BackgroundableProcessIndicator(task) {
      @Override
      public void cancel() {
        try {//ensure that serverSocket.accept was interrupted
          if (!myServerSocket.isClosed()) {
            new Socket(InetAddress.getLocalHost(), myServerSocket.getLocalPort());
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
        super.cancel();
      }
    };
    ProgressManagerImpl.runProcessWithProgressAsynchronously(task, mySearchForTestsIndicator);
    return task;
  }

  private static boolean isSyncSearch() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  protected void notifyByBalloon(JUnitRunningModel model, final JUnitConsoleProperties consoleProperties) {
    if (myFoundTests) {
      super.notifyByBalloon(model, consoleProperties);
    }
    else {
      final String packageName = myConfiguration.getPackage();
      if (packageName == null) return;
      final Project project = myConfiguration.getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
      if (aPackage == null) return;
      final Module module = myConfiguration.getConfigurationModule().getModule();
      if (module == null) return;
      final Set<Module> modulesWithPackage = new HashSet<Module>();
      final PsiDirectory[] directories = aPackage.getDirectories();
      for (PsiDirectory directory : directories) {
        final Module currentModule = ModuleUtil.findModuleForFile(directory.getVirtualFile(), project);
        if (module != currentModule) {
          modulesWithPackage.add(currentModule);
        }
      }
      if (!modulesWithPackage.isEmpty()) {
        final String testRunDebugId = consoleProperties.isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        final Function<Module, String> moduleNameRef = new Function<Module, String>() {
          @Override
          public String fun(Module module) {
            final String moduleName = module.getName();
            return "<a href=\"" + moduleName + "\">" + moduleName + "</a>";
          }
        };
        String message = "Tests were not found in module \"" + module.getName() + "\".\n" +
                         "Use ";
        if (modulesWithPackage.size() == 1) {
          message += "module \"" + moduleNameRef.fun(modulesWithPackage.iterator().next()) + "\" ";
        }
        else {
          message += "one of\n" + StringUtil.join(modulesWithPackage, moduleNameRef, "\n") + "\n";
        }
        message += "instead";
        toolWindowManager.notifyByBalloon(testRunDebugId, MessageType.WARNING, message, null,
                                          new ResetConfigurationModuleAdapter(project, consoleProperties, toolWindowManager,
                                                                              testRunDebugId));
      }
    }
  }

  public interface FindCallback {
    /**
     * Invoked in dispatch thread
     */
    void found(@NotNull Collection<PsiClass> classes, final boolean isJunit4);
  }

  private class MySearchForTestsTask extends Task.Backgroundable {
    private Socket mySocket;
    private final TestClassFilter myClassFilter;
    private final boolean[] myJunit4;
    private final THashSet<PsiClass> myClasses;
    private final FindCallback myCallback;

    public MySearchForTestsTask(TestClassFilter classFilter, boolean[] junit4, THashSet<PsiClass> classes, FindCallback callback) {
      super(classFilter.getProject(), ExecutionBundle.message("seaching.test.progress.title"), true);
      myClassFilter = classFilter;
      myJunit4 = junit4;
      myClasses = classes;
      myCallback = callback;
    }


    public void run(@NotNull ProgressIndicator indicator) {
      try {
        mySocket = myServerSocket.accept();
      }
      catch (IOException e) {
        LOG.info(e);
      }
      myJunit4[0] = ConfigurationUtil.findAllTestClasses(myClassFilter, myClasses);
      myFoundTests = !myClasses.isEmpty();
    }

    @Override
    public void onSuccess() {
      myCallback.found(myClasses, myJunit4[0]);
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
          myServerSocket.close();
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }
    }
  }

  private class ResetConfigurationModuleAdapter extends HyperlinkAdapter {
    private final Project myProject;
    private final JUnitConsoleProperties myConsoleProperties;
    private final ToolWindowManager myToolWindowManager;
    private final String myTestRunDebugId;

    public ResetConfigurationModuleAdapter(final Project project,
                                           final JUnitConsoleProperties consoleProperties,
                                           final ToolWindowManager toolWindowManager,
                                           final String testRunDebugId) {
      myProject = project;
      myConsoleProperties = consoleProperties;
      myToolWindowManager = toolWindowManager;
      myTestRunDebugId = testRunDebugId;
    }

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      final Module moduleByName = ModuleManager.getInstance(myProject).findModuleByName(e.getDescription());
      if (moduleByName != null) {
        myConfiguration.getConfigurationModule().setModule(moduleByName);
        try {
          final Executor executor = myConsoleProperties.isDebug() ? DefaultDebugExecutor.getDebugExecutorInstance()
                                    : DefaultRunExecutor.getRunExecutorInstance();
          final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), myConfiguration);
          assert runner != null;
          runner.execute(executor,
                         new ExecutionEnvironment(myConfiguration, myProject, getRunnerSettings(), getConfigurationSettings(), null));
          final Balloon balloon = myToolWindowManager.getToolWindowBalloon(myTestRunDebugId);
          if (balloon != null) {
            balloon.hide();
          }
        }
        catch (ExecutionException e1) {
          LOG.error(e1);
        }
      }
    }
  }
}
