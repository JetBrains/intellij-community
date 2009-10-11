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
