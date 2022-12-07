// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

final class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe {
  private StatusBar statusBar;
  private final Project project;

  ToggleReadOnlyAttributePanel(@NotNull Project project) {
    this.project = project;

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFilePropertyChangeEvent &&
              VirtualFile.PROP_WRITABLE.equals(((VFilePropertyChangeEvent)event).getPropertyName())) {
            if (statusBar != null) {
              statusBar.updateWidget(ID());
            }
          }
        }
      }
    });
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (statusBar != null) {
          statusBar.updateWidget(ID());
        }
      }
    });
  }

  @Override
  public @NotNull String ID() {
    return StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL;
  }

  @Override
  public StatusBarWidget copy() {
    return new ToggleReadOnlyAttributePanel(project);
  }

  @Override
  public WidgetPresentation getPresentation() {
    return new StatusBarWidget.IconPresentation() {
      @Override
      public @Nullable Icon getIcon() {
        if (!isReadonlyApplicable()) {
          return null;
        }

        VirtualFile virtualFile = getCurrentFile();
        return virtualFile == null || virtualFile.isWritable() ? AllIcons.Ide.Readwrite : AllIcons.Ide.Readonly;
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
          VirtualFile file = getCurrentFile();
          if (!isReadOnlyApplicableForFile(file)) {
            return;
          }

          FileDocumentManager.getInstance().saveAllDocuments();
          try {
            WriteAction.run(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable()));
            statusBar.updateWidget(ID());
          }
          catch (IOException e) {
            Messages.showMessageDialog(getProject(), e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
          }
        };
      }
    };
  }

  @Override
  public void dispose() {
    statusBar = null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    this.statusBar = statusBar;
  }

  private boolean isReadonlyApplicable() {
    return isReadOnlyApplicableForFile(getCurrentFile());
  }

  private static boolean isReadOnlyApplicableForFile(@Nullable VirtualFile file) {
    return file != null && !file.getFileSystem().isReadOnly();
  }

  private @Nullable Project getProject() {
    return statusBar != null ? statusBar.getProject() : null;
  }

  private @Nullable VirtualFile getCurrentFile() {
    Project project = getProject();
    if (project == null) {
      return null;
    }

    var editorSupplier = statusBar.getCurrentEditor();
    FileEditor editor = editorSupplier.invoke();
    return editor == null ? null : editor.getFile();
  }
}
