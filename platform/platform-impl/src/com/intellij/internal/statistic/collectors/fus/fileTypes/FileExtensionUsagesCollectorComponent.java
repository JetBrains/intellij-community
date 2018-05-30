// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class FileExtensionUsagesCollectorComponent extends AbstractProjectComponent {
  public FileExtensionUsagesCollectorComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    MessageBusConnection myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        FUSProjectUsageTrigger.getInstance(myProject).trigger(FileExtensionOpenUsageTriggerCollector.class, file.getExtension() != null ? file.getExtension() : file.getName());
        FUSProjectUsageTrigger.getInstance(myProject).trigger(FileTypeOpenUsageTriggerCollector.class, file.getFileType().getName());
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      }
    });
    ActionManager.getInstance().addAnActionListener(new AnActionListener() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof EditorAction && ((EditorAction)action).getHandler() instanceof EditorWriteActionHandler) {
          onChange(dataContext);
        }
      }

      private void onChange(DataContext dataContext) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor == null || editor.getProject() != myProject) return;
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file != null) {
          FUSProjectUsageTrigger.getInstance(myProject).trigger(FileExtensionEditUsageTriggerCollector.class,
                                                                file.getExtension() != null ? file.getExtension() : file.getName());
          FUSProjectUsageTrigger.getInstance(myProject).trigger(FileTypeEditUsageTriggerCollector.class, file.getFileType().getName());
        }
      }

      @Override
      public void beforeEditorTyping(char c, DataContext dataContext) {
        onChange(dataContext);
      }
    }, myProject);
  }
}
