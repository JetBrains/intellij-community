/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

@Deprecated
abstract class BaseDiffAction extends AnAction implements PreloadableAction, DumbAware {
  public void actionPerformed(AnActionEvent e) {
    DiffRequest diffData = getDiffData(e.getDataContext());
    if (diffData == null) return;
    final DiffContent[] contents = diffData.getContents();
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (DiffContent content : contents) {
          Document document = content.getDocument();
          if (document != null) {
            documentManager.saveDocument(document);
          }
        }
      }
    });
    DiffManager.getInstance().getDiffTool().show(diffData);
  }

  public void update(AnActionEvent e) {
    DiffRequest diffData = getDiffData(e.getDataContext());
    
    boolean enabled = diffData != null &&
                      (!diffData.isSafeToCallFromUpdate() || (diffData.getContents() != null && DiffManager.getInstance().getDiffTool().canShow(diffData)));

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

  public void preload() {
    if (!ApplicationManager.getApplication().isDisposed()) {
      DiffManager.getInstance();
    }
  }
}
