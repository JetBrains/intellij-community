// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      public void focusGained(@NotNull Editor editor) {
        repaintComboBox();
      }

      @Override
      public void focusLost(@NotNull Editor editor) {
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
        JComboBox comboBox = ComponentUtil.getParentOfType((Class<? extends JComboBox>)JComboBox.class, (Component)this);
        if (comboBox != null) {
          comboBox.repaint();
        }
      });
    }
  }
}
