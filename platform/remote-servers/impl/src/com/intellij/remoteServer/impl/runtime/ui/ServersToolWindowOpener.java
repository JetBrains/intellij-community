package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author michael.golubev
 */
public class ServersToolWindowOpener extends AbstractProjectComponent {

  public ServersToolWindowOpener(final Project project) {
    super(project);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
          public void run() {
            new ServersToolWindow(myProject);
          }
        });
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "ServersToolWindowOpener";
  }
}
