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

import com.android.ddmlib.*;
import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";

  public String ACTIVITY_CLASS = "";
  public String MODE = LAUNCH_DEFAULT_ACTIVITY;
  public boolean DEPLOY = true;

  public AndroidRunConfiguration(String name, Project project, ConfigurationFactory factory) {
    super(name, project, factory);
  }

  protected void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException {
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      Project project = configurationModule.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
      if (activityClass == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("cant.find.activity.class.error"));
      }
      PsiClass c = configurationModule.checkClassName(ACTIVITY_CLASS, AndroidBundle.message("activity.class.not.specified.error"));
      if (!c.isInheritor(activityClass, true)) {
        throw new RuntimeConfigurationError(AndroidBundle.message("not.activity.subclass.error", ACTIVITY_CLASS));
      }
      if (!AndroidUtils.isActivityLaunchable(facet.getModule(), c)) {
        throw new RuntimeConfigurationError(AndroidBundle.message("activity.not.launchable.error", AndroidUtils.LAUNCH_ACTION_NAME));
      }
    }
    else if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        if (AndroidUtils.getDefaultActivityName(manifest) != null) return;
      }
      throw new RuntimeConfigurationError(AndroidBundle.message("default.activity.not.found.error"));
    }
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    RunProfileState state = super.getState(executor, env);
    if (state != null) {
      assert state instanceof AndroidRunningState;
      ((AndroidRunningState)state).setDeploy(DEPLOY);
    }
    return state;
  }

  protected ModuleBasedConfiguration createInstance() {
    return new AndroidRunConfiguration(getName(), getProject(), AndroidRunConfigurationType.getInstance().getFactory());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidRunConfiguration> editor = new AndroidRunConfigurationEditor<AndroidRunConfiguration>(project);
    editor.setConfigurationSpecificEditor(new ApplicationRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @Nullable
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiClass && Comparing.strEqual(((PsiClass)element).getQualifiedName(), ACTIVITY_CLASS, true)) {
      return new RefactoringElementAdapter() {
        public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          ACTIVITY_CLASS = ((PsiClass)newElement).getQualifiedName();
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          ACTIVITY_CLASS = oldQualifiedName;
        }
      };
    }
    return null;
  }

  @NotNull
  @Override
  protected ConsoleView attachConsole(AndroidRunningState state, Executor executor) {
    Project project = getConfigurationModule().getProject();
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    ConsoleView console = builder.getConsole();
    console.attachToProcess(state.getProcessHandler());
    return console;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return true;
  }

  @Nullable
  @Override
  protected AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet) {
    String activityToLaunch = null;
    if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      Manifest manifest = facet.getManifest();
      if (manifest == null) return null;
      String defaultActivityName = AndroidUtils.getDefaultActivityName(manifest);
      if (defaultActivityName != null) {
        activityToLaunch = defaultActivityName;
      }
      else {
        Messages.showErrorDialog(facet.getModule().getProject(), AndroidBundle.message("default.activity.not.found.error"),
                                 CommonBundle.getErrorTitle());
        return null;
      }
    }
    else if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      activityToLaunch = ACTIVITY_CLASS;
    }
    return new MyApplicationLauncher(activityToLaunch);
  }

  private static class MyApplicationLauncher extends AndroidApplicationLauncher {
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunConfiguration.MyApplicationLauncher");
    private final String myActivityName;

    private MyApplicationLauncher(@Nullable String activityName) {
      myActivityName = activityName;
    }

    @SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
    @Override
    public boolean isReadyForDebugging(ClientData data, ProcessHandler processHandler) {
      if (myActivityName == null) {
        ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
        switch (status) {
          case ERROR:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debug port is busy\n", STDOUT);
            }
            LOG.info("Debug port is busy");
            return false;
          case ATTACHED:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debugger already attached\n", STDOUT);
            }
            LOG.info("Debugger already attached");
            return false;
          default:
            return true;
        }
      }
      return super.isReadyForDebugging(data, processHandler);
    }

    public boolean launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
      throws IOException, AdbCommandRejectedException, TimeoutException {
      if (myActivityName == null) return true;
      final String activityPath = state.getPackageName() + '/' + myActivityName;
      ProcessHandler processHandler = state.getProcessHandler();
      if (state.isStopped()) return false;
      processHandler.notifyTextAvailable("Launching application: " + activityPath + ".\n", STDOUT);
      AndroidRunningState.MyReceiver receiver = state.new MyReceiver();
      boolean debug = state.isDebugMode();
      while (true) {
        if (state.isStopped()) return false;
        String command = "am start " + (debug ? "-D " : "") + "-n \"" + activityPath + "\"";
        boolean deviceNotResponding = false;
        try {
          state.executeDeviceCommandAndWriteToConsole(device, command, receiver);
        }
        catch (ShellCommandUnresponsiveException e) {
          LOG.info(e);
          deviceNotResponding = true;
        }
        if (!deviceNotResponding && receiver.getErrorType() != 2) {
          break;
        }
        processHandler.notifyTextAvailable("Device is not ready. Waiting for " + AndroidRunningState.WAITING_TIME + " sec.\n", STDOUT);
        synchronized (state.getRunningLock()) {
          try {
            state.getRunningLock().wait(AndroidRunningState.WAITING_TIME * 1000);
          }
          catch (InterruptedException e) {
          }
        }
        receiver = state.new MyReceiver();
      }
      boolean success = receiver.getErrorType() == AndroidRunningState.NO_ERROR;
      if (success) {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDOUT);
      }
      else {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDERR);
      }
      return success;
    }
  }
}
