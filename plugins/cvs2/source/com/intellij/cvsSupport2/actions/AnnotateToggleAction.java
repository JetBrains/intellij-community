package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.util.Collection;

/**
 * author: lesya
 */
public class AnnotateToggleAction extends ToggleAction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.AnnotateToggleAction");

  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public AnnotateToggleAction() {
    myVisibility.shouldNotBePerformedOnDirectory();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_EXIST_IN_CVS);
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_NOT_DELETED);
    myVisibility.addCondition(new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        if (context.getEditor() != null) return true;
        VirtualFile selectedFile = context.getSelectedFile();
        if (selectedFile == null) return false;
        return hasTextEditor(selectedFile);
      }
    });
  }

  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  private boolean hasTextEditor(VirtualFile selectedFile) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(selectedFile);
    return !fileType.isBinary();
  }


  public boolean isSelected(AnActionEvent e) {
    VcsContext context = CvsContextWrapper.on(e);
    Editor editor = context.getEditor();
    if (editor == null) return false;
    Object annotations = editor.getUserData(AnnotateAction.KEY_IN_EDITOR);
    if (annotations == null) return false;
    return !((Collection)annotations).isEmpty();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    VcsContext context = CvsContextWrapper.on(e);
    Editor editor = context.getEditor();
    if (!state) {
      if (editor == null) {
        return;
      }
      else {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      if (editor == null) {
        VirtualFile selectedFile = context.getSelectedFile();
        FileEditor[] fileEditors = FileEditorManager.getInstance(context.getProject()).openFile(selectedFile, false);
        for (int i = 0; i < fileEditors.length; i++) {
          FileEditor fileEditor = fileEditors[i];
          if (fileEditor instanceof TextEditor){
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }

      LOG.assertTrue(editor != null);

      new AnnotateAction(editor).actionPerformed(e);

    }
  }
}
