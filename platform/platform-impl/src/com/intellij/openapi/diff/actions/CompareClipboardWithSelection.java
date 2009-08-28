package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

public class CompareClipboardWithSelection extends BaseDiffAction {

  @Nullable
  protected DiffRequest getDiffData(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    Editor editorData = PlatformDataKeys.EDITOR.getData(dataContext);
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

    public DiffContent[] getContents() {
      if (myContents != null) return myContents;
      DiffContent clipboardContent = createClipboardContent();
      if (clipboardContent == null) return null;
      myContents = new DiffContent[2];
      myContents[0] = clipboardContent;

      SelectionModel selectionModel = myEditor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        myContents[1] = new FragmentContent(DocumentContent.fromDocument(getProject(), getDocument()),
                                            range, getProject(), getDocumentFile(getDocument()));
      }
      else {
        myContents [1] = DocumentContent.fromDocument(getProject(), getDocument());
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

    private static DiffContent createClipboardContent() {
      Transferable content = CopyPasteManager.getInstance().getContents();
      String text;
      try {
        text = (String) (content.getTransferData(DataFlavor.stringFlavor));
      } catch (Exception e) {
        return null;
      }
      return text != null ? new SimpleContent(text) : null;
    }
  }
}
