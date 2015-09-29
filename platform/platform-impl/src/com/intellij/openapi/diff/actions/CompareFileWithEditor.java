/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @deprecated use {@link com.intellij.diff.actions.CompareFileWithEditorAction} instead
 */
@Deprecated
public class CompareFileWithEditor extends BaseDiffAction {
  @Nullable
  private static Document getEditingDocument(final DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length == 0) return null;
    VirtualFile selectedFile = selectedFiles[0];
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    // find document for latest selected editor
    if (editor != null) {
      for (VirtualFile file : selectedFiles) {
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == editor.getDocument()) {
          selectedFile = file;
        }
      }
    }
    if (!DiffContentUtil.isTextFile(selectedFile)) return null;
    return FileDocumentManager.getInstance().getDocument(selectedFile);
  }

  public void update(AnActionEvent e) {
    boolean enabled = true;
    Presentation presentation = e.getPresentation();
    presentation.setText(DiffBundle.message("diff.compare.element.type.with.editor.action.name"));
    if (getDiffData(e.getDataContext()) == null) {
      enabled = false;
    }
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  protected FileEditorContents getDiffData(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    VirtualFile[] array = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    Document document = getEditingDocument(dataContext);
    if (array == null || array.length != 1 || document == null) {
      return null;
    }
    if (array[0].isDirectory()) {
      return null;
    }
    if (isSameFile(document, array [0])) {
      return null;
    }
    return new FileEditorContents(document, array [0], project);
  }

  private static boolean isSameFile(Document document, VirtualFile elementFile) {
    VirtualFile documentFile = FileDocumentManager.getInstance().getFile(document);

    return documentFile != null && documentFile.isValid() &&
           documentFile.equals(elementFile);
  }

  protected void disableAction(Presentation presentation) {
    presentation.setVisible(false);
  }


  private static class FileEditorContents extends DiffRequest {
    private final VirtualFile myFile;
    private final Document myDocument;

    public FileEditorContents(Document document, VirtualFile file, Project project) {
      super(project);
      myDocument = document;
      myFile = file;
    }

    public String[] getContentTitles() {
      VirtualFile documentFile = getDocumentFile(myDocument);
      String documentTitle = documentFile != null
                             ? getVirtualFileContentTitle(documentFile)
                             : DiffBundle.message("diff.content.editor.content.title");
      return new String[]{getVirtualFileContentTitle(myFile), documentTitle};
    }

    @NotNull
    public DiffContent[] getContents() {
      return new DiffContent[]{
        DiffContent.fromFile(getProject(), myFile),
        DiffContent.fromDocument(getProject(), myDocument)
      };
    }

    public String getWindowTitle() {
      if (isEditorContent(myDocument)) {
        return DiffBundle.message("diff.element.qualified.name.vs.editor.dialog.title", getVirtualFileContentTitle(myFile));
      } else {
        return DiffBundle.message("diff.element.qualified.name.vs.file.dialog.title", getVirtualFileContentTitle(myFile),
                                  getVirtualFileContentTitle(getDocumentFile(myDocument)));
      }

    }

    @NonNls
    @Override
    public String toString() {
      return "FileEditorContents:" + myFile.getPath();
    }
  }
}
