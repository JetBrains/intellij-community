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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.util.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;

public class ToggleReadOnlyAttributePanel implements StatusBarWidget, StatusBarWidget.IconPresentation {
  private static final Icon myLockedIcon = IconLoader.getIcon("/ide/readonly.png");
  private static final Icon myUnlockedIcon = IconLoader.getIcon("/ide/readwrite.png");

  private StatusBar myStatusBar;

  @NotNull
  public Icon getIcon() {
    final Editor editor = getEditor();
    return editor == null || editor.getDocument().isWritable() ? myUnlockedIcon : myLockedIcon;
  }

  @NotNull
  public String ID() {
    return "ReadOnlyAttribute";
  }

  public WidgetPresentation getPresentation(@NotNull Type type) {
    return this;
  }

  public void dispose() {
    myStatusBar = null;
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  public String getTooltipText() {
    return isReadonlyApplicable() ? UIBundle.message("read.only.attr.panel.double.click.to.toggle.attr.tooltip.text") : null;
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        final Project project = getProject();
        if (project == null) {
          return;
        }
        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
        final VirtualFile[] files = editorManager.getSelectedFiles();
        if (!isReadOnlyApplicableForFiles(files)) {
          return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(files[0], files[0].isWritable());
              myStatusBar.updateWidget(ID());
            }
            catch (IOException e) {
              Messages.showMessageDialog(project, e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
            }
          }
        });
      }
    };
  }

  private boolean isReadonlyApplicable() {
    final Project project = getProject();
    if (project == null) return false;
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    if (editorManager == null) return false;
    VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
    return isReadOnlyApplicableForFiles(selectedFiles);
  }

  private static boolean isReadOnlyApplicableForFiles(final VirtualFile[] files) {
    return files.length > 0 && !files[0].getFileSystem().isReadOnly();
  }

  @Nullable
  private Editor getEditor() {
    final Project project = getProject();
    if (project != null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      return manager.getSelectedTextEditor();
    }

    return null;
  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((JComponent) myStatusBar));
  }
}
