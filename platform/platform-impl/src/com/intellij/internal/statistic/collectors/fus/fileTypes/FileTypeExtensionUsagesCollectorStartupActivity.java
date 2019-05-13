// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class FileTypeExtensionUsagesCollectorStartupActivity implements StartupActivity {
  private static final Key<Long> LAST_EDIT_USAGE = Key.create("LAST_EDIT_USAGE");

  @Override
  public void runActivity(@NotNull Project project) {
    MessageBusConnection myConnection = project.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        FileTypeOpenUsageTriggerCollector.trigger(project, file.getFileType());
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (action instanceof EditorAction && ((EditorAction)action).getHandler() instanceof EditorWriteActionHandler) {
          onChange(dataContext);
        }
      }

      private void onChange(DataContext dataContext) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor == null || editor.getProject() != project) return;
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file != null) {
          Long lastEdit = editor.getUserData(LAST_EDIT_USAGE);
          if (lastEdit == null || System.currentTimeMillis() - lastEdit > 60 * 1000) {
            editor.putUserData(LAST_EDIT_USAGE, System.currentTimeMillis());
            FileTypeEditUsageTriggerCollector.trigger(project, file.getFileType());
          }
        }
      }

      @Override
      public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
        onChange(dataContext);
      }
    });
  }
}
