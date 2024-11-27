package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

public class OpenInEditorAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    JourneyDiagramDataModel diagramDataModel = editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL);
    if (diagramDataModel == null) return;
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (virtualFile == null) return;
    int offset = editor.getCaretModel().getOffset();
    Project project = e.getProject();
    openInEditor(virtualFile, project, offset);
  }

  private static void openInEditor(VirtualFile virtualFile, Project project, int offset) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, offset);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    fileEditorManager.openTextEditor(descriptor, true);
  }
}
