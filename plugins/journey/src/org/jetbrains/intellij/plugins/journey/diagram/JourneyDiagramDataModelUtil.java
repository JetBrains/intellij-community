package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramFileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class JourneyDiagramDataModelUtil {

  /**
   * Finds nodes containing the specified file.
   */
  public static @NotNull List<JourneyNode> findNodesForFile(JourneyDiagramDataModel model, @NotNull VirtualFile file) {
    return model.getNodes().stream()
      .filter(node -> Objects.equals(node.getIdentifyingElement().getFile().getVirtualFile(), file))
      .toList();
  }

  @RequiresEdt
  public static void openTabAndFocus(JourneyDiagramDataModel model){
    DiagramFileEditor editor = model.getBuilder().getEditor();
    if (editor == null) return;
    VirtualFile virtualFile = editor.getOriginalVirtualFile();
    FileEditorManager.getInstance(model.getProject()).openFileEditor(new OpenFileDescriptor(model.getProject(), virtualFile), true);
  }

}
