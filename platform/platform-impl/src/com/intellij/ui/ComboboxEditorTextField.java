/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ComboboxEditorTextField extends EditorTextField {

  public ComboboxEditorTextField(@NotNull String text, Project project, FileType fileType) {
    super(text, project, fileType);
    setOneLineMode(true);
  }

  public ComboboxEditorTextField(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false);
    setOneLineMode(true);
  }

  public ComboboxEditorTextField(Document document, Project project, FileType fileType, boolean isViewer) {
    super(document, project, fileType, isViewer);
    setOneLineMode(true);
  }

  @Override
  protected boolean shouldHaveBorder() {
    return UIManager.getBorder("ComboBox.border") == null && !UIUtil.isUnderDarcula() && !UIUtil.isUnderIntelliJLaF();
  }

  @Override
  protected void updateBorder(@NotNull EditorEx editor) {}

  @Override
  protected EditorEx createEditor() {
    EditorEx result = super.createEditor();

    result.addFocusListener(new FocusChangeListener() {
      @Override
      public void focusGained(Editor editor) {
        repaintComboBox();
      }

      @Override
      public void focusLost(Editor editor) {
        repaintComboBox();
      }
    });

    return result;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    return new Dimension(preferredSize.width, UIUtil.fixComboBoxHeight(preferredSize.height));
  }

  private void repaintComboBox() {
    // TODO:
    if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF() || (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel())) {
      IdeFocusManager.getInstance(getProject()).doWhenFocusSettlesDown(() -> {
        JComboBox comboBox = UIUtil.getParentOfType(JComboBox.class, this);
        if (comboBox != null) {
          comboBox.repaint();
        }
      });
    }
  }
}
