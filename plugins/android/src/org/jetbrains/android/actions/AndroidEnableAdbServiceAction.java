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

import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.logcat.AndroidLogcatToolWindowFactory;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    setAdbServiceEnabled(project, state);
  }

  public static void setAdbServiceEnabled(Project project, boolean state) {
    boolean oldState = isAdbServiceEnabled();
    PropertiesComponent.getInstance().setValue(ENABLE_ADB_SERVICE_PROPERTY_NAME, Boolean.toString(state));
    if (oldState != state) {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidLogcatToolWindowFactory.TOOL_WINDOW_ID);
      boolean hidden = false;
      if (toolWindow != null && toolWindow.isVisible()) {
        hidden = true;
        toolWindow.hide(null);
      }
      AndroidSdkData.terminateDdmlib();
      if (hidden) {
        toolWindow.show(null);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }
}
