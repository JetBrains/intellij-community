// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class MemoryDiskConflictResolver {
  private static final Logger LOG = Logger.getInstance(MemoryDiskConflictResolver.class);

  private final Set<VirtualFile> myConflicts = new LinkedHashSet<>();
  private Throwable myConflictAppeared;

  void beforeContentChange(@NotNull VFileContentChangeEvent event) {
    if (event.isFromSave()) return;

    VirtualFile file = event.getFile();
    if (!file.isValid() || hasConflict(file)) return;

    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document == null || !FileDocumentManager.getInstance().isDocumentUnsaved(document)) return;

    long documentStamp = document.getModificationStamp();
    long oldFileStamp = event.getOldModificationStamp();
    if (documentStamp != oldFileStamp) {
      LOG.info("reload " + file.getName() + " from disk?");
      LOG.info("  documentStamp:" + documentStamp);
      LOG.info("  oldFileStamp:" + oldFileStamp);
      if (myConflicts.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.info("  fileStamp:" + event.getModificationStamp());
          LOG.info("  document content:" + document.getText());
          myConflictAppeared = new Throwable();
        }
        ApplicationManager.getApplication().invokeLater(() -> processConflicts());
      }
      myConflicts.add(file);
    }
  }

  boolean hasConflict(VirtualFile file) {
    return myConflicts.contains(file);
  }

  private void processConflicts() {
    List<VirtualFile> conflicts = new ArrayList<>(myConflicts);
    myConflicts.clear();

    for (VirtualFile file : conflicts) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null && file.getModificationStamp() != document.getModificationStamp() && askReloadFromDisk(file, document)) {
        FileDocumentManager.getInstance().reloadFromDisk(document);
      }
    }
    myConflictAppeared = null;
  }

  @VisibleForTesting
  protected boolean askReloadFromDisk(VirtualFile file, Document document) {
    if (myConflictAppeared != null) {
      Throwable trace = myConflictAppeared;
      myConflictAppeared = null;
      throw new IllegalStateException("Unexpected memory-disk conflict in tests for " + file.getPath() +
                                      ", please use FileDocumentManager#reloadFromDisk or avoid VFS refresh", trace);
    }

    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());

    DialogBuilder builder = new DialogBuilder();
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.addAction(new AbstractAction(UIBundle.message("file.cache.conflict.show.difference.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(file);
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