/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link com.intellij.diff.actions.CompareClipboardWithSelectionAction} instead
 */
@Deprecated
public class CompareClipboardWithSelection extends BaseDiffAction {
  @Nullable
  protected DiffRequest getDiffData(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    Editor editorData = CommonDataKeys.EDITOR.getData(dataContext);
    Editor editor = editorData != null ? editorData : FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return null;
    return new ClipboardSelectionContents(editor, project);
  }

  private static class ClipboardSelectionContents extends DiffRequest {
    private DiffContent[] myContents = null;
    private final Editor myEditor;

    public ClipboardSelectionContents(Editor editor, Project project) {
      super(project);
      myEditor = editor;
    }

    public String[] getContentTitles() {
      return new String[]{DiffBundle.message("diff.content.clipboard.content.title"),
        isEditorContent(getDocument()) ?
        DiffBundle.message("diff.content.selection.from.editor.content.title") :
        DiffBundle.message("diff.content.selection.from.file.content.title", getDocumentFileUrl(getDocument()))
      };
    }

    @Override
    public boolean isSafeToCallFromUpdate() {
      return !SystemInfo.isMac;
    }

    @NotNull
    public DiffContent[] getContents() {
      if (myContents != null) return myContents;
      DiffContent clipboardContent = ClipboardVsValueContents.createClipboardContent();
      if (clipboardContent == null) clipboardContent = new SimpleContent("");
      myContents = new DiffContent[2];
      myContents[0] = clipboardContent;

      SelectionModel selectionModel = myEditor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        boolean forceReadOnly = myEditor.isViewer();
        myContents[1] = new FragmentContent(DiffContent.fromDocument(getProject(), getDocument()),
                                            range, getProject(), getDocumentFile(getDocument()), forceReadOnly);
      }
      else {
        myContents [1] = DiffContent.fromDocument(getProject(), getDocument());
      }
      return myContents;
    }

    private Document getDocument() {
      return myEditor.getDocument();
    }

    public String getWindowTitle() {
      if (isEditorContent(getDocument())) {
        return DiffBundle.message("diff.clipboard.vs.editor.dialog.title");
      } else {
        return DiffBundle.message("diff.clipboard.vs.file.dialog.title", getDocumentFileUrl(getDocument()));
      }
    }

  }
}
