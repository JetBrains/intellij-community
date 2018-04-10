/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ServersToolWindowManager {
  @NotNull private final Project myProject;
  private final String myWindowId;
  private final Icon myIcon;

  public ServersToolWindowManager(@NotNull Project project, String windowId, Icon icon) {
    myProject = project;
    myWindowId = windowId;
    myIcon = icon;

    StartupManager.getInstance(project).registerPostStartupActivity(() -> setupListeners());
  }

  public void setupListeners() {
    getFactory().getContribution().setupAvailabilityListener(myProject, () -> updateWindowAvailable(true));
    myProject.getMessageBus().connect().subscribe(RemoteServerListener.TOPIC, new RemoteServerListener() {
      @Override
      public void serverAdded(@NotNull RemoteServer<?> server) {
        updateWindowAvailable(true);
      }

      @Override
      public void serverRemoved(@NotNull RemoteServer<?> server) {
        updateWindowAvailable(false);
      }
    });
  }

  private void updateWindowAvailable(final boolean showIfAvailable) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

    toolWindowManager.invokeLater(() -> {
      boolean available = getFactory().getContribution().canContribute(myProject);

      final ToolWindow toolWindow = toolWindowManager.getToolWindow(myWindowId);

      if (toolWindow == null) {
        if (available) {
          createToolWindow(myProject, toolWindowManager, false).show(null);
        }
        return;
      }

      doUpdateWindowAvailable(toolWindow, showIfAvailable, available);
    });
  }

  protected void doUpdateWindowAvailable(@NotNull ToolWindow toolWindow, boolean showIfAvailable, boolean available) {
    boolean doShow = !toolWindow.isAvailable() && available;
    if (toolWindow.isAvailable() && !available) {
      toolWindow.hide(null);
    }
    toolWindow.setAvailable(available, null);
    if (showIfAvailable && doShow) {
      toolWindow.show(null);
    }
  }

  /**
   * @deprecated use {@link #createToolWindow(Project, ToolWindowManager, boolean)}
   */
  @Deprecated
  protected ToolWindow createToolWindow(Project project, ToolWindowManager toolWindowManager) {
    return createToolWindow(project, toolWindowManager, false);
  }

  protected ToolWindow createToolWindow(Project project, ToolWindowManager toolWindowManager, boolean deferContentCreation) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(myWindowId, false, ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(myIcon);

    Runnable createContent = () -> getFactory().createToolWindowContent(project, toolWindow);
    if (deferContentCreation) {
      UiNotifyConnector.doWhenFirstShown(toolWindow.getContentManager().getComponent(), createContent);
    }
    else {
      createContent.run();
    }
    return toolWindow;
  }

  @NotNull
  protected abstract ServersToolWindowFactory getFactory();

  @NotNull
  protected final Project getProject() {
    return myProject;
  }
}
