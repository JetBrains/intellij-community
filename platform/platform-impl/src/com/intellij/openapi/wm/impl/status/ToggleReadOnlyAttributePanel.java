// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.IOException;

public final class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {
  private StatusBar myStatusBar;

  @Override
  @Nullable
  public Icon getIcon() {
    if (!isReadonlyApplicable()) {
      return null;
    }
    VirtualFile virtualFile = getCurrentFile();
    return virtualFile == null || virtualFile.isWritable() ? AllIcons.Ide.Readwrite : AllIcons.Ide.Readonly;
  }

  @Override
  @NotNull
  public String ID() {
    return StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL;
  }

  @Override
  public StatusBarWidget copy() {
    return new ToggleReadOnlyAttributePanel();
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void dispose() {
    myStatusBar = null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    Project project = statusBar.getProject();
    if (project == null) {
      return;
    }

    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
          if (myStatusBar != null) {
            myStatusBar.updateWidget(ID());
          }
        }
    });
  }

  @Override
  public String getTooltipText() {
    VirtualFile virtualFile = getCurrentFile();
    int writable = virtualFile == null || virtualFile.isWritable() ? 1 : 0;
    int readonly = writable == 1 ? 0 : 1;
    return ActionsBundle.message("action.ToggleReadOnlyAttribute.files", readonly, writable, 1, 0);
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      final VirtualFile file = getCurrentFile();
      if (!isReadOnlyApplicableForFile(file)) {
        return;
      }
      FileDocumentManager.getInstance().saveAllDocuments();

      try {
        WriteAction.run(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable()));
        myStatusBar.updateWidget(ID());
      }
      catch (IOException e) {
        Messages.showMessageDialog(getProject(), e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      }
    };
  }

  private boolean isReadonlyApplicable() {
    VirtualFile file = getCurrentFile();
    return isReadOnlyApplicableForFile(file);
  }

  private static boolean isReadOnlyApplicableForFile(@Nullable VirtualFile file) {
    return file != null && !file.getFileSystem().isReadOnly();
  }

  @Nullable
  private Project getProject() {
    return myStatusBar != null ? myStatusBar.getProject() : null;
  }

  @Nullable
  private VirtualFile getCurrentFile() {
    final Project project = getProject();
    if (project == null) return null;
    EditorsSplitters splitters = FileEditorManagerEx.getInstanceEx(project).getSplittersFor(myStatusBar.getComponent());
    return splitters.getCurrentFile();
  }
}
