/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation, FileEditorManagerListener {
  private Project myProject;
  private StatusBar myStatusBar;

  public ToggleReadOnlyAttributePanel(@NotNull Project project) {
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Override
  @NotNull
  public Icon getIcon() {
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
    return new ToggleReadOnlyAttributePanel(myProject);
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void dispose() {
    myStatusBar = null;
    myProject = null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  @Override
  public String getTooltipText() {
    return isReadonlyApplicable() ? UIBundle.message("read.only.attr.panel.double.click.to.toggle.attr.tooltip.text") : null;
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
    return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((JComponent) myStatusBar));
  }
  
  @Nullable
  private VirtualFile getCurrentFile() {
    final Project project = getProject();
    if (project == null) return null;
    EditorsSplitters splitters = FileEditorManagerEx.getInstanceEx(project).getSplittersFor(myStatusBar.getComponent());
    return splitters.getCurrentFile();
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
  }
}
