// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class EditorSettingsManager implements EditorFactoryListener {
  // Handles the following EditorConfig settings:
  public static final String maxLineLengthKey = "max_line_length";

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    applyEditorSettings(event.getEditor());
  }

  public static void applyEditorSettings(Editor editor) {
    Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return;
    Project project = editor.getProject();
    if (project == null) return;
    if (!Utils.isEnabled(CodeStyle.getSettings(project))) return;

    List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, file);
    String maxLineLength = Utils.configValueForKey(outPairs, maxLineLengthKey);
    applyMaxLineLength(project, maxLineLength, editor, file);
  }

  private static void applyMaxLineLength(Project project, String maxLineLength, Editor editor, VirtualFile file) {
    if (maxLineLength.isEmpty()) return;
    if ("off".equals(maxLineLength)) {
      setRightMarginShown(editor, false);
      return;
    }
    try {
      int length = Integer.parseInt(maxLineLength);
      if (length < 0) {
        Utils.invalidConfigMessage(project, maxLineLength, maxLineLengthKey, file.getCanonicalPath());
        return;
      }
      setRightMarginShown(editor, true);
      editor.getSettings().setRightMargin(length);
    } catch (NumberFormatException e) {
      Utils.invalidConfigMessage(project, maxLineLength, maxLineLengthKey, file.getCanonicalPath());
    }
  }

  private static void setRightMarginShown(@NotNull Editor editor, boolean isShown) {
    Color rightMarginColor =
      isShown ? AbstractColorsScheme.INHERITED_COLOR_MARKER : null;
    editor.getColorsScheme().setColor(EditorColors.RIGHT_MARGIN_COLOR, rightMarginColor);
  }
}
