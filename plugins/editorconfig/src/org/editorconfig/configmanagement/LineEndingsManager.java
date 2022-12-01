// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel;
import com.intellij.util.LineSeparator;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LineEndingsManager implements FileDocumentManagerListener {
  // Handles the following EditorConfig settings:
  public static final String lineEndingsKey = "end_of_line";

  private boolean statusBarUpdated = false;

  @Override
  public void beforeAllDocumentsSaving() {
    statusBarUpdated = false;
  }

  private static void updateStatusBar(Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
      StatusBar statusBar = frame != null ? frame.getStatusBar() : null;
      StatusBarWidget widget = statusBar == null ? null : statusBar.getWidget(StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL);
      if (widget instanceof LineSeparatorPanel) {
        ((LineSeparatorPanel)widget).selectionChanged(null);
      }
    });
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return;
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    if (project != null) {
      applySettings(project, file);
    }
  }

  private void applySettings(Project project, VirtualFile file) {
    if (!Utils.isEnabled(CodeStyle.getSettings(project))) {
      return;
    }

    final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, file);
    final String lineEndings = Utils.configValueForKey(outPairs, lineEndingsKey);
    if (lineEndings.isEmpty()) {
      return;
    }

    try {
      LineSeparator separator = LineSeparator.valueOf(StringUtil.toUpperCase(lineEndings));
      String oldSeparator = file.getDetectedLineSeparator();
      String newSeparator = separator.getSeparatorString();
      if (!StringUtil.equals(oldSeparator, newSeparator)) {
        file.setDetectedLineSeparator(newSeparator);
        if (!statusBarUpdated) {
          statusBarUpdated = true;
          updateStatusBar(project);
        }
      }
    }
    catch (IllegalArgumentException e) {
      Utils.invalidConfigMessage(project, lineEndings, lineEndingsKey, file.getCanonicalPath());
    }
  }
}
