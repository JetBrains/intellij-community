package com.intellij.platform;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FilesystemToolwindowOpener extends AbstractProjectComponent {
  public FilesystemToolwindowOpener(final Project project) {
    super(project);
  }

  public void projectOpened() {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir();
    if (baseDir == null || !baseDir.isDirectory()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
              public void run() {
                new FilesystemToolwindow(baseDir, myProject);
              }
            });
          }
        });
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "FilesystemToolwindowOpener";
  }
}
