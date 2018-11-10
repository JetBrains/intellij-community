// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
abstract class BaseDiffAction extends AnAction implements PreloadableAction, DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffRequest diffData = getDiffData(e.getDataContext());
    if (diffData == null) return;
    final DiffContent[] contents = diffData.getContents();
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (DiffContent content : contents) {
        Document document = content.getDocument();
        if (document != null) {
          documentManager.saveDocument(document);
        }
      }
    });
    DiffManager.getInstance().getDiffTool().show(diffData);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DiffRequest diffData = getDiffData(e.getDataContext());

    boolean enabled = diffData != null && DiffManager.getInstance().getDiffTool().canShow(diffData);

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    if (enabled) presentation.setVisible(true);
    else disableAction(presentation);
  }

  protected void disableAction(Presentation presentation) {}

  @Nullable
  protected abstract DiffRequest getDiffData(DataContext dataContext);

  protected static VirtualFile getDocumentFile(Document document) {
    return FileDocumentManager.getInstance().getFile(document);
  }

  protected static boolean isEditorContent(Document document) {
    VirtualFile editorFile = getDocumentFile(document);
    return editorFile == null || !editorFile.isValid();
  }

  protected static String getDocumentFileUrl(Document document) {
    return getDocumentFile(document).getPresentableUrl();
  }

  protected static String getContentTitle(Document document) {
    VirtualFile editorFile = getDocumentFile(document);
    if (editorFile == null || !editorFile.isValid())
      return DiffBundle.message("diff.content.editor.content.title");
    return editorFile.getPresentableUrl();
  }

  protected static String getVirtualFileContentTitle(final VirtualFile documentFile) {
    String name = documentFile.getName();
    VirtualFile parent = documentFile.getParent();
    if (parent != null) {
      return name + " (" + FileUtil.toSystemDependentName(parent.getPath()) + ")";
    }
    return name;
  }

  @Override
  public void preload() {
    if (!ApplicationManager.getApplication().isDisposed()) {
      DiffManager.getInstance();
    }
  }
}
