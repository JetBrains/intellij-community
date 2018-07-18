// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class MoveEditorToOppositeTabGroupAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (vFile == null || project == null){
      return;
    }
    final EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    if (window != null) {
      final EditorWindow[] siblings = window.findSiblings();
      if (siblings != null && siblings.length == 1) {
        final EditorWithProviderComposite editorComposite = window.getSelectedEditor();
        final HistoryEntry entry = editorComposite.currentStateAsHistoryEntry();
        vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);
        closeOldFile(vFile, window);
        ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project)).openFileImpl3(siblings[0], vFile, true, entry, true);
        vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
      }
    }
  }

  protected void closeOldFile(VirtualFile vFile, EditorWindow window) {
    window.closeFile(vFile, true, false);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    final EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(isEnabled(vFile, window));
    }
    else {
      presentation.setEnabled(isEnabled(vFile, window));
    }
  }

  private static boolean isEnabled(VirtualFile vFile, EditorWindow window) {
    if (vFile != null && window != null) {
      final EditorWindow[] siblings = window.findSiblings ();
      if (siblings != null && siblings.length == 1) {
        return true;
      }
    }
    return false;
  }
}
