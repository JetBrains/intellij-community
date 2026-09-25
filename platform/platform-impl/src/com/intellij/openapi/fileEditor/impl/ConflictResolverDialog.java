// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.event.ActionEvent;

final class ConflictResolverDialog {
  private final @Nullable Project project;

  ConflictResolverDialog(@Nullable Project project) {
    this.project = project;
  }

  boolean askReloadFromDisk(@NotNull VirtualFile file, @NotNull Document document) {
    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());
    DialogBuilder builder = new DialogBuilder(project);
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.addAction(new AbstractAction(UIBundle.message("file.cache.conflict.show.difference.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        String fsContent = LoadTextUtil.loadText(file).toString();
        DocumentContent content1 = DiffContentFactory.getInstance().create(project, fsContent, file.getFileType());
        DocumentContent content2 = DiffContentFactory.getInstance().create(project, document, file);
        String title = UIBundle.message("file.cache.conflict.for.file.dialog.title", file.getPresentableUrl());
        String title1 = UIBundle.message("file.cache.conflict.diff.content.file.system.content");
        String title2 = UIBundle.message("file.cache.conflict.diff.content.memory.content");
        DiffRequest request = new SimpleDiffRequest(title, content1, content2, title1, title2);
        request.putUserData(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, true);
        DialogBuilder diffBuilder = new DialogBuilder(project);
        DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(project, diffBuilder, diffBuilder.getWindow());
        diffPanel.setRequest(request);
        diffBuilder.setCenterPanel(diffPanel.getComponent());
        diffBuilder.setDimensionServiceKey("FileDocumentManager.FileCacheConflict");
        diffBuilder.addOkAction().setText(UIBundle.message("file.cache.conflict.save.changes.button"));
        diffBuilder.addCancelAction();
        diffBuilder.setTitle(title);
        if (diffBuilder.show() == DialogWrapper.OK_EXIT_CODE) {
          builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      }
    });
    builder.setTitle(UIBundle.message("file.cache.conflict.dialog.title"));
    builder.setHelpId("reference.dialogs.fileCacheConflict");
    return builder.show() == 0;
  }
}
