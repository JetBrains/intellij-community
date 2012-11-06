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
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiClass;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import com.intellij.xdebugger.XDebuggerBundle;
import icons.AndroidIcons;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidDebugRunner extends DefaultProgramRunner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidDebugRunner");

  public static final Key<AndroidSessionInfo> ANDROID_SESSION_INFO = new Key<AndroidSessionInfo>("ANDROID_SESSION_INFO");
  private static final Object myReaderLock = new Object();

  private static final Object myDebugLock = new Object();
  @NonNls private static final String ANDROID_DEBUG_SELECTED_TAB_PROPERTY = "ANDROID_DEBUG_SELECTED_TAB";
  public static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";

  private static void tryToCloseOldSessions(final Executor executor, Project project) {
    final ExecutionManager manager = ExecutionManager.getInstance(project);
    ProcessHandler[] processes = manager.getRunningProcesses();
    for (ProcessHandler process : processes) {
      final AndroidSessionInfo info = process.getUserData(ANDROID_SESSION_INFO);
      if (info != null) {
        process.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                manager.getContentManager().removeRunContent(executor, info.getDescriptor());
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

      final RunContentDescriptor descriptor = embedToExistingSession(project, executor, runningState);
      runDescriptor = descriptor != null ? descriptor : super.doExecute(project, executor, state, contentToReuse, environment);
      launcher.setRunDescriptor(runDescriptor);
      if (descriptor != null) {
        return null;
      }
    }
    if (runDescriptor == null) {
      return null;
    }
    tryToCloseOldSessions(executor, project);
    final ProcessHandler handler = runningState.getProcessHandler();
    handler.putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(
      runDescriptor, runningState, executor.getId()));
    runningState.setRestarter(runDescriptor.getRestarter());
    return runDescriptor;
  }

  @Nullable
  private static Pair<ProcessHandler, AndroidSessionInfo> findOldSession(Project project,
                                                                         Executor executor,
                                                                         AndroidRunConfigurationBase configuration) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      final AndroidSessionInfo info = handler.getUserData(ANDROID_SESSION_INFO);

      if (info != null &&
          info.getState().getConfiguration().equals(configuration) &&
          executor.getId().equals(info.getExecutorId()) &&
          !handler.isProcessTerminated()) {
        return Pair.create(handler, info);
      }
    }
    return null;
  }

  private static RunContentDescriptor embedToExistingSession(Project project, Executor executor, final AndroidRunningState state) {
    final Pair<ProcessHandler, AndroidSessionInfo> pair = findOldSession(project, executor, state.getConfiguration());
    final AndroidSessionInfo oldSessionInfo = pair != null ? pair.getSecond() : null;
    final ProcessHandler oldProcessHandler = pair != null ? pair.getFirst() : null;

    if (oldSessionInfo == null || oldProcessHandler == null) {
      return null;
    }
    final AndroidExecutionState oldState = oldSessionInfo.getState();
    final IDevice[] oldDevices = oldState.getDevices();
    final ConsoleView oldConsole = oldState.getConsoleView();

    if (oldDevices == null ||
        oldConsole == null ||
        oldDevices.length == 0 ||
        oldDevices.length > 1) {
      return null;
    }
    final Ref<List<IDevice>> devicesRef = Ref.create();

    final boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        devicesRef.set(state.getAllCompatibleDevices());
      }
    }, "Scanning available devices", false, project);

    if (!result) {
      return null;
    }
    final List<IDevice> devices = devicesRef.get();

    if (devices.size() == 0 ||
        devices.size() > 1 ||
        devices.get(0) != oldDevices[0]) {
      return null;
    }
    oldProcessHandler.detachIsDefault();
    state.setTargetDevices(devices.toArray(new IDevice[devices.size()]));
    state.setConsole(oldConsole);
    final DefaultDebugProcessHandler newProcessHandler = new DefaultDebugProcessHandler();
    oldSessionInfo.getDescriptor().setProcessHandler(newProcessHandler);
    state.setProcessHandler(newProcessHandler);
    newProcessHandler.startNotify();
    oldConsole.attachToProcess(newProcessHandler);
    AndroidProcessText.attach(newProcessHandler);
    newProcessHandler.notifyTextAvailable("The session is restarted\n", STDOUT);

    NotificationGroup.toolWindowGroup("Android Session Restarted", ToolWindowId.DEBUG, true)
      .createNotification("Session '" + oldSessionInfo.getDescriptor().getDisplayName() + "' is restarted", MessageType.INFO)
      .notify(project);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        state.start();
      }
    });
    return oldSessionInfo.getDescriptor();
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

  private static class AndroidDebugState implements RemoteState, AndroidExecutionState {
    private final Project myProject;
    private final RemoteConnection myConnection;
    private final RunnerSettings myRunnerSettings;
    private final ConfigurationPerRunnerSettings myConfigurationSettings;
    private final AndroidRunningState myState;
    private final IDevice myDevice;

    private volatile ConsoleView myConsoleView;

    public AndroidDebugState(Project project,
                             RemoteConnection connection,
                             RunnerSettings runnerSettings,
                             ConfigurationPerRunnerSettings configurationSettings,
                             AndroidRunningState state,
                             IDevice device) {
      myProject = project;
      myConnection = connection;
      myRunnerSettings = runnerSettings;
      myConfigurationSettings = configurationSettings;
      myState = state;
      myDevice = device;
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
      myConsoleView = myState.getConfiguration().attachConsole(myState, executor);
      //final boolean resetSelectedTab = myState.getConfiguration() instanceof AndroidRunConfiguration;
      final MyLogcatExecutionConsole console = new MyLogcatExecutionConsole(myProject, myDevice, process, myConsoleView, true);
      return new DefaultExecutionResult(console, process);
    }

    public RemoteConnection getRemoteConnection() {
      return myConnection;
    }

    @Override
    public IDevice[] getDevices() {
      return new IDevice[]{myDevice};
    }

    @Nullable
    @Override
    public ConsoleView getConsoleView() {
      return myConsoleView;
    }

    @NotNull
    @Override
    public AndroidRunConfigurationBase getConfiguration() {
      return myState.getConfiguration();
    }
  }

  @NotNull
  public String getRunnerId() {
    return "AndroidDebugRunner";
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof AndroidRunConfigurationBase;
  }

  private static class MyLogcatExecutionConsole implements ExecutionConsoleEx, ObservableConsoleView {
    private final Project myProject;
    private final AndroidLogcatView myToolWindowView;
    private final ConsoleView myConsoleView;
    private final boolean myResetSelectedTab;

    private MyLogcatExecutionConsole(Project project,
                                     IDevice device,
                                     RemoteDebugProcessHandler process,
                                     ConsoleView consoleView,
                                     boolean resetSelectedTab) {
      myProject = project;
      myConsoleView = consoleView;
      myResetSelectedTab = resetSelectedTab;
      myToolWindowView = new AndroidLogcatView(project, device, true) {
        @Override
        protected boolean isActive() {
          final DebuggerSessionTab sessionTab = DebuggerPanelsManager.getInstance(myProject).getSessionTab();
          if (sessionTab == null) {
            return false;
          }
          final Content content = sessionTab.getUi().findContent(ANDROID_LOGCAT_CONTENT_ID);
          return content != null && content.isSelected();
        }
      };
      Disposer.register(this, myToolWindowView);
      myToolWindowView.getLogConsole().attachStopLogConsoleTrackingListener(process);
    }

    @Override
    public void buildUi(final RunnerLayoutUi layoutUi) {
      final Content consoleContent = layoutUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, getComponent(),
                                                            XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                                            AllIcons.Debugger.Console, getPreferredFocusableComponent());

      consoleContent.setCloseable(false);
      layoutUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);

      // todo: provide other icon
      final Content logcatContent = layoutUi.createContent(ANDROID_LOGCAT_CONTENT_ID, myToolWindowView.getContentPanel(), "Logcat",
                                                         AndroidIcons.Android, getPreferredFocusableComponent());
      logcatContent.setCloseable(false);
      logcatContent.setSearchComponent(myToolWindowView.createSearchComponent(myProject));
      layoutUi.addContent(logcatContent, 2, PlaceInGrid.bottom, false);

      if (myResetSelectedTab) {
        final String tabName = PropertiesComponent.getInstance().getValue(ANDROID_DEBUG_SELECTED_TAB_PROPERTY);
        Content selectedContent = logcatContent;

        if (tabName != null) {
          for (Content content : layoutUi.getContents()) {
            if (tabName.equals(content.getDisplayName())) {
              selectedContent = content;
            }
          }
        }
        layoutUi.getContentManager().setSelectedContent(selectedContent);
      }

      layoutUi.addListener(new ContentManagerAdapter() {
        public void selectionChanged(final ContentManagerEvent event) {
          if (myResetSelectedTab) {
            final Content content = event.getContent();

            if (content.isSelected()) {
              PropertiesComponent.getInstance().setValue(ANDROID_DEBUG_SELECTED_TAB_PROPERTY, content.getDisplayName());
            }
          }
          myToolWindowView.activate();
        }
      }, myToolWindowView);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myToolWindowView.activate();
        }
      });
    }

    @Nullable
    @Override
    public String getExecutionConsoleId() {
      return "ANDROID_LOGCAT";
    }

    @Override
    public JComponent getComponent() {
      return myConsoleView.getComponent();
    }

    @Override
    public JComponent getPreferredFocusableComponent() {
      return myConsoleView.getPreferredFocusableComponent();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
      if (myConsoleView instanceof ObservableConsoleView) {
        ((ObservableConsoleView)myConsoleView).addChangeListener(listener, parent);
      }
    }
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
          AndroidDebugState st =
            new AndroidDebugState(myProject, new RemoteConnection(true, "localhost", debugPort, false), myEnvironment.getRunnerSettings(),
                                  myEnvironment.getConfigurationSettings(), myRunningState, device);
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
          final AndroidProcessText oldText = AndroidProcessText.get(processHandler);
          if (oldText != null) {
            oldText.printTo(newProcessHandler);
          }
          AndroidProcessText.attach(newProcessHandler);

          myRunningState.getProcessHandler().putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(
            debugDescriptor, st, myExecutor.getId()));

          final DebuggerSessionTab sessionTab = manager.getSessionTab();
          assert sessionTab != null;
          sessionTab.setEnvironment(myEnvironment);

          RunProfile profile = myEnvironment.getRunProfile();
          assert profile instanceof AndroidRunConfigurationBase;
          RunContentManager runContentManager = ExecutionManager.getInstance(myProject).getContentManager();
          runContentManager.showRunContent(myExecutor, debugDescriptor);
          newProcessHandler.startNotify();
        }
      });
    }
  }
}
