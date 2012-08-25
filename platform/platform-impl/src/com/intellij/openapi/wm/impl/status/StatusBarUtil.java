/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class StatusBarUtil {
  private StatusBarUtil() { }

  /**
   * Finds the current file editor.
   */
  @Nullable
  public static FileEditor getCurrentFileEditor(@NotNull Project project, @Nullable StatusBar statusBar) {
    if (statusBar == null) {
      return null;
    }

    DockContainer c = DockManager.getInstance(project).getContainerFor(statusBar.getComponent());
    EditorsSplitters splitters = null;
    if (c instanceof DockableEditorTabbedContainer) {
      splitters = ((DockableEditorTabbedContainer)c).getSplitters();
    }

    if (splitters != null && splitters.getCurrentWindow() != null) {
      EditorWithProviderComposite editor = splitters.getCurrentWindow().getSelectedEditor();
      if (editor != null) {
        return editor.getSelectedEditorWithProvider().getFirst();
      }
    }
    return null;
  }

  public static void setStatusBarInfo(@NotNull Project project, @NotNull @Nls String message) {
    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(message);
    }
  }
}
