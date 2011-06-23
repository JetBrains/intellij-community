/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogConsoleListener;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.logcat.AndroidLogcatUtil;
import org.jetbrains.android.logcat.AndroidLoggingReader;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;

/**
 * @author coyote
 */
public class AndroidDebugRunner extends DefaultProgramRunner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidDebugRunner");

  private static final Key<RunContentDescriptor> ANDROID_PROCESS_HANDLER = new Key<RunContentDescriptor>("ANDROID_PROCESS_HANDLER");
  private static final Object myReaderLock = new Object();

  private static final Object myDebugLock = new Object();

  private static void tryToCloseOldSessions(final Executor executor, Project project) {
    final ExecutionManager manager = ExecutionManager.getInstance(project);
    ProcessHandler[] processes = manager.getRunningProcesses();
    for (ProcessHandler process : processes) {
      final RunContentDescriptor descriptor = process.getUserData(ANDROID_PROCESS_HANDLER);
      if (descriptor != null) {
        process.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                manager.getContentManager().removeRunContent(executor, descriptor);
              }
            });
          }
        });
        process.detachProcess();
      }
    }
  }

  @Override
  protected RunContentDescriptor doExecute(final Project project,
                                           final Executor executor,
                                           final RunProfileState state,
                                           final RunContentDescriptor contentToReuse,
                                           final ExecutionEnvironment environment) throws ExecutionException {
    assert state instanceof AndroidRunningState;
    final RunProfile runProfile = environment.getRunProfile();
    final AndroidRunningState runningState = (AndroidRunningState)state;
    if (runProfile instanceof AndroidTestRunConfiguration) {
      String targetPackage = getTargetPackage((AndroidTestRunConfiguration)runProfile, runningState);
      if (targetPackage == null) {
        throw new ExecutionException(AndroidBundle.message("target.package.not.specified.error"));
      }
      runningState.setTargetPackageName(targetPackage);
    }
    runningState.setDebugMode(true);
    RunContentDescriptor runDescriptor;
    synchronized (myDebugLock) {
      MyDebugLauncher launcher = new MyDebugLauncher(project, executor, runningState, environment);
      runningState.setDebugLauncher(launcher);
      runDescriptor = super.doExecute(project, executor, state, contentToReuse, environment);
      launcher.setRunDescriptor(runDescriptor);
    }
    if (runDescriptor == null) {
      throw new ExecutionException("Can't run an application");
    }
    tryToCloseOldSessions(executor, project);
    runningState.getProcessHandler().putUserData(ANDROID_PROCESS_HANDLER, runDescriptor);
    return runDescriptor;
  }

  @Nullable
  private static String getTargetPackage(AndroidTestRunConfiguration configuration, AndroidRunningState state) {
    Manifest manifest = state.getFacet().getManifest();
    assert manifest != null;
    for (Instrumentation instrumentation : manifest.getInstrumentations()) {
      PsiClass c = instrumentation.getInstrumentationClass().getValue();
      String runner = configuration.INSTRUMENTATION_RUNNER_CLASS;
      if (c != null && (runner.length() == 0 || runner.equals(c.getQualifiedName()))) {
        String targetPackage = instrumentation.getTargetPackage().getValue();
        if (targetPackage != null) {
          return targetPackage;
        }
      }
    }
    return null;
  }

  private static class AndroidDebugState implements RemoteState {
    private final Project myProject;
    private final RemoteConnection myConnection;
    private final RunnerSettings myRunnerSettings;
    private final ConfigurationPerRunnerSettings myConfigurationSettings;
    private final AndroidRunningState myState;

    public AndroidDebugState(Project project,
                             RemoteConnection connection,
                             RunnerSettings runnerSettings,
                             ConfigurationPerRunnerSettings configurationSettings,
                             AndroidRunningState state) {
      myProject = project;
      myConnection = connection;
      myRunnerSettings = runnerSettings;
      myConfigurationSettings = configurationSettings;
      myState = state;
    }

    public RunnerSettings getRunnerSettings() {
      return myRunnerSettings;
    }

    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return myConfigurationSettings;
    }

    public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
      RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
      myState.setProcessHandler(process);
      ConsoleView console = myState.attachConsole();
      return new DefaultExecutionResult(console, process);
    }

    public RemoteConnection getRemoteConnection() {
      return myConnection;
    }
  }

  @NotNull
  public String getRunnerId() {
    return "AndroidDebugRunner";
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof AndroidRunConfigurationBase;
  }

  private class MyDebugLauncher implements DebugLauncher {
    private final Project myProject;
    private final Executor myExecutor;
    private final AndroidRunningState myRunningState;
    private final ExecutionEnvironment myEnvironment;
    private RunContentDescriptor myRunDescriptor;

    public MyDebugLauncher(Project project,
                           Executor executor,
                           AndroidRunningState state,
                           ExecutionEnvironment environment) {
      myProject = project;
      myExecutor = executor;
      myRunningState = state;
      myEnvironment = environment;
    }

    public void setRunDescriptor(RunContentDescriptor runDescriptor) {
      myRunDescriptor = runDescriptor;
    }

    public void launchDebug(final IDevice device, final String debugPort) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
        public void run() {
          final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(myProject);
          RemoteState st =
            new AndroidDebugState(myProject, new RemoteConnection(true, "localhost", debugPort, false), myEnvironment.getRunnerSettings(),
                                  myEnvironment.getConfigurationSettings(), myRunningState);
          RunContentDescriptor debugDescriptor = null;
          final ProcessHandler processHandler = myRunningState.getProcessHandler();
          try {
            synchronized (myDebugLock) {
              assert myRunDescriptor != null;
              debugDescriptor = manager
                .attachVirtualMachine(myExecutor, AndroidDebugRunner.this, myEnvironment, st, myRunDescriptor, st.getRemoteConnection(),
                                      false);
            }
          }
          catch (ExecutionException e) {
            processHandler.notifyTextAvailable("ExecutionException: " + e.getMessage() + '.', STDERR);
          }
          ProcessHandler newProcessHandler = debugDescriptor != null ? debugDescriptor.getProcessHandler() : null;
          if (debugDescriptor == null || newProcessHandler == null) {
            processHandler.notifyTextAvailable("Can't start debugging.", STDERR);
            processHandler.destroyProcess();
            return;
          }
          processHandler.detachProcess();

          myRunningState.getProcessHandler().putUserData(ANDROID_PROCESS_HANDLER, debugDescriptor);

          DebuggerSessionTab sessionTab = manager.getSessionTab();
          assert sessionTab != null;
          sessionTab.setEnvironment(myEnvironment);
          RunProfile profile = myEnvironment.getRunProfile();
          assert profile instanceof AndroidRunConfigurationBase;
          final Reader[] readerWrapper = new Reader[1];
          final Writer[] writerWrapper = new Writer[1];
          String logcatTabTitle = AndroidBundle.message("android.logcat.tab.title");

          LogConsoleBase console = sessionTab.addLogConsole(logcatTabTitle, new AndroidLoggingReader() {
            @NotNull
            protected Object getLock() {
              return myReaderLock;
            }

            protected Reader getReader() {
              return readerWrapper[0];
            }
          }, 0, AndroidUtils.ANDROID_ICON);

          console.addListener(new LogConsoleListener() {
            @Override
            public void loggingWillBeStopped() {
              final Writer writer = writerWrapper[0];
              if (writer != null) {
                try {
                  writer.close();
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            }
          });

          synchronized (myReaderLock) {
            final Pair<Reader,Writer> pair =
              AndroidLogcatUtil.startLoggingThread(myProject, device, ((AndroidRunConfigurationBase)profile).CLEAR_LOGCAT, console);
            if (pair != null) {
              readerWrapper[0] = pair.first;
              writerWrapper[0] = pair.second;
            }
            else {
              readerWrapper[0] = null;
              writerWrapper[0] = null;
            }
          }
          if (!(profile instanceof AndroidTestRunConfiguration)) {
            String logcatContentId = DebuggerSessionTab.getLogContentId(logcatTabTitle);
            if (logcatContentId != null) {
              sessionTab.getUi().getDefaults().initFocusContent(logcatContentId, LayoutViewOptions.STARTUP);
            }
          }
          RunContentManager runContentManager = ExecutionManager.getInstance(myProject).getContentManager();
          runContentManager.showRunContent(myExecutor, debugDescriptor);
          newProcessHandler.startNotify();
        }
      });
    }
  }
}
