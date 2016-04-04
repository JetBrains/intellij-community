/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import icons.RemoteServersIcons;
import org.jetbrains.annotations.NotNull;

public abstract class ServersToolWindowManager extends AbstractProjectComponent {

  private final String myWindowId;

  public ServersToolWindowManager(final Project project, String windowId) {
    super(project);
    myWindowId = windowId;
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        setupListeners();
      }
    });
  }

  public void setupListeners() {
    getFactory().getContribution().setupAvailabilityListener(myProject, new Runnable() {
      @Override
      public void run() {
        updateWindowAvailable(true);
      }
    });
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

    toolWindowManager.invokeLater(new Runnable() {

      @Override
      public void run() {
        boolean available = getFactory().getContribution().canContribute(myProject);

        final ToolWindow toolWindow = toolWindowManager.getToolWindow(myWindowId);

        if (toolWindow == null) {
          if (available) {
            createToolWindow(myProject, toolWindowManager).show(null);
          }
          return;
        }

        boolean doShow = !toolWindow.isAvailable() && available;
        if (toolWindow.isAvailable() && !available) {
          toolWindow.hide(null);
        }
        toolWindow.setAvailable(available, null);
        if (showIfAvailable && doShow) {
          toolWindow.show(null);
        }
      }
    });
  }

  private ToolWindow createToolWindow(Project project, ToolWindowManager toolWindowManager) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(myWindowId, false, ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(RemoteServersIcons.ServersToolWindow);
    getFactory().createToolWindowContent(project, toolWindow);
    return toolWindow;
  }

  @NotNull
  protected abstract ServersToolWindowFactory getFactory();
}
