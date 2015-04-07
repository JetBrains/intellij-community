package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Context of the search to be done
 */
public final class SearchContext {
  private final PsiFile file;
  private final Project project;

  private SearchContext(Project project, PsiFile file) {
    this.project = project;
    this.file = file;
  }

  public PsiFile getFile() {
    return file;
  }

  public Project getProject() {
    return project;
  }

  public static SearchContext buildFromDataContext(DataContext context) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (vFile != null && (file == null || !vFile.equals(file.getContainingFile().getVirtualFile()))) {
      file = PsiManager.getInstance(project).findFile(vFile);
    }
    return new SearchContext(project, file);
  }

  public Editor getEditor() {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }
}
