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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
class MemoryDiskConflictResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.MemoryDiskConflictResolver");
  private final Set<VirtualFile> myConflicts = new LinkedHashSet<>();
  private Throwable myConflictAppeared;

  void beforeContentChange(@NotNull VirtualFileEvent event) {
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
          myConflictAppeared = new Throwable();
        }
        ApplicationManager.getApplication().invokeLater(this::processConflicts);
      }
      myConflicts.add(file);
    }
  }

  boolean hasConflict(VirtualFile file) {
    return myConflicts.contains(file);
  }

  private void processConflicts() {
    ArrayList<VirtualFile> conflicts = new ArrayList<>(myConflicts);
    myConflicts.clear();

    for (VirtualFile file : conflicts) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null && file.getModificationStamp() != document.getModificationStamp() && askReloadFromDisk(file, document)) {
        FileDocumentManager.getInstance().reloadFromDisk(document);
      }
    }
    myConflictAppeared = null;
  }

  boolean askReloadFromDisk(VirtualFile file, Document document) {
    if (myConflictAppeared != null) {
      Throwable trace = myConflictAppeared;
      myConflictAppeared = null;
      throw new IllegalStateException("Unexpected memory-disk conflict in tests, please use FileDocumentManager#reloadFromDisk or avoid VFS refresh", trace);
    }
    
    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());

    final DialogBuilder builder = new DialogBuilder();
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.addAction(new AbstractAction(UIBundle.message("file.cache.conflict.show.difference.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ProjectEx project = (ProjectEx)ProjectLocator.getInstance().guessProjectForFile(file);

        FileType fileType = file.getFileType();
        String fsContent = LoadTextUtil.loadText(file).toString();
        DocumentContent content1 = DiffContentFactory.getInstance().create(project, fsContent, fileType);
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
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.setHelpId("reference.dialogs.fileCacheConflict");
    return builder.show() == 0;
  }

}
