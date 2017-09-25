package org.editorconfig.configmanagement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorSettingsManager extends EditorFactoryAdapter {
  // Handles the following EditorConfig settings:
  public static final String maxLineLengthKey = "max_line_length";
  private Project myProject;

  public EditorSettingsManager(Project project) {
    myProject = project;
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    applyEditorSettings(event.getEditor());
  }

  public void applyEditorSettings(Editor editor) {
    Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return;
    if (!Utils.isEnabled(CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings())) return;

    List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(myProject, file);
    String maxLineLength = Utils.configValueForKey(outPairs, maxLineLengthKey);
    applyMaxLineLength(maxLineLength, editor, file);
  }

  private void applyMaxLineLength(String maxLineLength, Editor editor, VirtualFile file) {
    if (maxLineLength.isEmpty()) return;
    if ("off".equals(maxLineLength)) {
      editor.getSettings().setRightMarginShown(false);
      return;
    }
    try {
      int length = Integer.parseInt(maxLineLength);
      if (length < 0) {
        Utils.invalidConfigMessage(myProject, maxLineLength, maxLineLengthKey, file.getCanonicalPath());
        return;
      }
      editor.getSettings().setRightMarginShown(true);
      editor.getSettings().setRightMargin(length);
    } catch (NumberFormatException e) {
      Utils.invalidConfigMessage(myProject, maxLineLength, maxLineLengthKey, file.getCanonicalPath());
    }
  }
}
