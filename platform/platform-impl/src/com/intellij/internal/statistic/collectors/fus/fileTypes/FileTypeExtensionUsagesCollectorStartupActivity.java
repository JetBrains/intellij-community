// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger;
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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class FileTypeExtensionUsagesCollectorStartupActivity implements StartupActivity {
  private static final Cache<Pair<String, String>, Boolean> EDIT_USAGE_ONE_MINUTE_THROTTLING_CACHE = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build();

  @Override
  public void runActivity(@NotNull Project project) {
    MessageBusConnection myConnection = project.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        FUSProjectUsageTrigger.getInstance(project).trigger(FileExtensionOpenUsageTriggerCollector.class, file.getExtension() != null ? file.getExtension() : file.getName());
        FUSProjectUsageTrigger.getInstance(project).trigger(FileTypeOpenUsageTriggerCollector.class, file.getFileType().getName());
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        String extension = file.getExtension() != null ? file.getExtension() : file.getName();
        String fileType = file.getFileType().getName();
        EDIT_USAGE_ONE_MINUTE_THROTTLING_CACHE.invalidate(Pair.create(file.getPath(), extension));
        EDIT_USAGE_ONE_MINUTE_THROTTLING_CACHE.invalidate(Pair.create(file.getPath(), fileType));
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, AnActionEvent event) {
        if (action instanceof EditorAction && ((EditorAction)action).getHandler() instanceof EditorWriteActionHandler) {
          onChange(dataContext);
        }
      }

      private void onChange(DataContext dataContext) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor == null || editor.getProject() != project) return;
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file != null) {
          String extension = file.getExtension() != null ? file.getExtension() : file.getName();
          String fileType = file.getFileType().getName();
          if (EDIT_USAGE_ONE_MINUTE_THROTTLING_CACHE.asMap().putIfAbsent(Pair.create(file.getPath(), extension), Boolean.TRUE) == null)
            FUSProjectUsageTrigger.getInstance(project).trigger(FileExtensionEditUsageTriggerCollector.class, extension);
          if (EDIT_USAGE_ONE_MINUTE_THROTTLING_CACHE.asMap().putIfAbsent(Pair.create(file.getPath(), fileType), Boolean.TRUE) == null)
            FUSProjectUsageTrigger.getInstance(project).trigger(FileTypeEditUsageTriggerCollector.class, fileType);
        }
      }

      @Override
      public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
        onChange(dataContext);
      }
    });
  }
}
