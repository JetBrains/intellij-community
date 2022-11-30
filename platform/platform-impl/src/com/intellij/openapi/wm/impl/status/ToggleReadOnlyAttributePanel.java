// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
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
import java.util.function.Supplier;

public final class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {
  private StatusBar myStatusBar;

  @Override
  public @Nullable Icon getIcon() {
    if (!isReadonlyApplicable()) {
      return null;
    }
    VirtualFile virtualFile = getCurrentFile();
    return virtualFile == null || virtualFile.isWritable() ? AllIcons.Ide.Readwrite : AllIcons.Ide.Readonly;
  }

  @Override
  public @NotNull String ID() {
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
    myStatusBar.updateWidget(ID());
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
            myStatusBar.updateWidget(ID());
          }
        }
      }));

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

  private @Nullable Project getProject() {
    return myStatusBar != null ? myStatusBar.getProject() : null;
  }

  private @Nullable VirtualFile getCurrentFile() {
    Project project = getProject();
    if (project == null) {
      return null;
    }

    Supplier<@Nullable FileEditor> editorSupplier = myStatusBar.getCurrentEditor();
    if (editorSupplier == null) {
      EditorsSplitters splitters = FileEditorManagerEx.getInstanceEx(project).getSplittersFor(myStatusBar.getComponent());
      return splitters.getCurrentFile();
    }
    else {
      FileEditor editor = editorSupplier.get();
      return editor == null ? null : editor.getFile();
    }
  }
}
