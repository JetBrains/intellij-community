package org.editorconfig.configmanagement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.LineSeparator;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * @author Dennis.Ushakov
 */
public class LineEndingsManager extends FileDocumentManagerAdapter {
  // Handles the following EditorConfig settings:
  public static final String lineEndingsKey = "end_of_line";

  private final Project myProject;
  private boolean statusBarUpdated = false;

  public LineEndingsManager(Project project) {
    this.myProject = project;
  }

  @Override
  public void beforeAllDocumentsSaving() {
    statusBarUpdated = false;
  }

  private void updateStatusBar() {
    ApplicationManager.getApplication().invokeLater(() -> {
      IdeFrame frame = WindowManager.getInstance().getIdeFrame(myProject);
      StatusBar statusBar = frame != null ? frame.getStatusBar() : null;
      StatusBarWidget widget = statusBar != null ? statusBar.getWidget("LineSeparator") : null;

      if (widget instanceof LineSeparatorPanel) {
        FileEditorManagerEvent event = new FileEditorManagerEvent(FileEditorManager.getInstance(myProject),
                                                                  null, null, null, null);
        ((LineSeparatorPanel)widget).selectionChanged(event);
      }
    });
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    applySettings(file);
  }

  private void applySettings(VirtualFile file) {
    if (file == null) return;
    if (!Utils.isEnabled(CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings())) return;

    final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(myProject, file);
    final String lineEndings = Utils.configValueForKey(outPairs, lineEndingsKey);
    if (!lineEndings.isEmpty()) {
      try {
        LineSeparator separator = LineSeparator.valueOf(lineEndings.toUpperCase(Locale.US));
        String oldSeparator = file.getDetectedLineSeparator();
        String newSeparator = separator.getSeparatorString();
        if (!StringUtil.equals(oldSeparator, newSeparator)) {
          file.setDetectedLineSeparator(newSeparator);
          if (!statusBarUpdated) {
            statusBarUpdated = true;
            updateStatusBar();
          }
        }
      }
      catch (IllegalArgumentException e) {
        Utils.invalidConfigMessage(myProject, lineEndings, lineEndingsKey, file.getCanonicalPath());
      }
    }
  }
}
