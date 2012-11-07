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
package org.jetbrains.android.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidDebugRunner;
import org.jetbrains.android.run.AndroidSessionInfo;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidEnableAdbServiceAction extends ToggleAction {
  private static final String ENABLE_ADB_SERVICE_PROPERTY_NAME = "AndroidEnableDdms";

  @SuppressWarnings({"UnusedDeclaration"})
  public AndroidEnableAdbServiceAction() {
    this(null);
  }

  public AndroidEnableAdbServiceAction(@Nullable Icon icon) {
    super(AndroidBundle.message("android.enable.adb.service.action.title"),
          AndroidBundle.message("android.enable.adb.service.action.description"), icon);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return isAdbServiceEnabled();
  }

  public static boolean isAdbServiceEnabled() {
    String enableDdmsProperty = PropertiesComponent.getInstance().getValue(ENABLE_ADB_SERVICE_PROPERTY_NAME);
    return enableDdmsProperty == null || Boolean.parseBoolean(enableDdmsProperty);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (state) {
      setAdbServiceEnabled(project, true);
    }
    else {
      disableAdbService(project);
    }
  }

  public static boolean disableAdbService(Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!askForClosingDebugSessions(project)) {
      return false;
    }
    setAdbServiceEnabled(project, false);
    return true;
  }

  public static void setAdbServiceEnabled(Project project, boolean state) {
    boolean oldState = isAdbServiceEnabled();
    PropertiesComponent.getInstance().setValue(ENABLE_ADB_SERVICE_PROPERTY_NAME, Boolean.toString(state));
    if (oldState != state) {
      AndroidSdkUtils.restartDdmlib(project);
    }
  }

  private static boolean askForClosingDebugSessions(@NotNull Project project) {
    final List<Pair<ProcessHandler, RunContentDescriptor>> pairs = new ArrayList<Pair<ProcessHandler, RunContentDescriptor>>();

    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      final ProcessHandler[] processes = ExecutionManager.getInstance(p).getRunningProcesses();

      for (ProcessHandler process : processes) {
        if (!process.isProcessTerminated()) {
          final AndroidSessionInfo info = process.getUserData(AndroidDebugRunner.ANDROID_SESSION_INFO);
          if (info != null) {
            pairs.add(Pair.create(process, info.getDescriptor()));
          }
        }
      }
    }

    if (pairs.size() == 0) {
      return true;
    }

    final StringBuilder s = new StringBuilder();

    for (Pair<ProcessHandler, RunContentDescriptor> pair : pairs) {
      if (s.length() > 0) {
        s.append('\n');
      }
      s.append(pair.getSecond().getDisplayName());
    }

    final int r = Messages.showYesNoDialog(project, AndroidBundle.message("android.debug.sessions.will.be.closed", s),
                                           AndroidBundle.message("android.disable.adb.service.title"), Messages.getQuestionIcon());
    return r == Messages.YES;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }
}
