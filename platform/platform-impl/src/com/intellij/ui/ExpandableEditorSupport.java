// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

public class ExpandableEditorSupport extends ExpandableSupport<EditorTextField> {
  public ExpandableEditorSupport(@NotNull EditorTextField field) {
    super(field, null, null);
    field.addSettingsProvider(editor -> {
      initFieldEditor(editor, field.getBackground());
      updateFieldFolding(editor);
    });
  }

  protected void initPopupEditor(@NotNull EditorEx editor, Color background) {
    JLabel label = ExpandableSupport.createLabel(createCollapseExtension());
    label.setBorder(JBUI.Borders.empty(5, 0, 5, 7));
    editor.getContentComponent().putClientProperty(Expandable.class, this);
    editor.getScrollPane().setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    editor.getScrollPane().getVerticalScrollBar().setBackground(background);
    editor.getScrollPane().getVerticalScrollBar().add(JBScrollBar.LEADING, label);
    editor.getScrollPane().setViewportBorder(JBUI.Borders.empty(4, 6));
  }

  protected void initFieldEditor(@NotNull EditorEx editor, Color background) {
    JLabel label = ExpandableSupport.createLabel(createExpandExtension());
    label.setBorder(JBUI.Borders.empty(2, 0));
    editor.getContentComponent().putClientProperty(Expandable.class, this);
    editor.getScrollPane().setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    editor.getScrollPane().getVerticalScrollBar().setBackground(background);
    editor.getScrollPane().getVerticalScrollBar().add(JBScrollBar.LEADING, label);
  }

  protected void updateFieldFolding(@NotNull EditorEx editor) {
    FoldingModelEx model = editor.getFoldingModel();
    CharSequence text = editor.getDocument().getCharsSequence();
    model.runBatchFoldingOperation(() -> {
      model.clearFoldRegions();
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
          FoldRegion region = model.createFoldRegion(i, i + 1, " \u23ce ", null, true);
          if (region != null) region.setExpanded(false);
        }
      }
    });
  }

  @NotNull
  @Override
  protected Content prepare(@NotNull EditorTextField field, @NotNull Function<? super String, String> onShow) {
    EditorTextField popup = new EditorTextField(onShow.fun(field.getText()));
    popup.setOneLineMode(false);
    popup.setPreferredSize(new Dimension(field.getWidth(), 5 * field.getHeight()));
    popup.addSettingsProvider(editor -> {
      initPopupEditor(editor, field.getBackground());
      copyCaretPosition(editor, field.getEditor());
    });
    return new Content() {
      @NotNull
      @Override
      public JComponent getContentComponent() {
        return popup;
      }

      @Override
      public JComponent getFocusableComponent() {
        return popup;
      }

      @Override
      public void cancel(@NotNull Function<? super String, String> onHide) {
        field.setText(onHide.fun(popup.getText()));
        Editor editor = field.getEditor();
        if (editor instanceof EditorEx) {
          updateFieldFolding((EditorEx)editor);
          copyCaretPosition(editor, popup.getEditor());
        }
      }
    };
  }

  private static void copyCaretPosition(@NotNull Editor destination, Editor source) {
    if (source == null) return; // unexpected
    try {
      destination.getCaretModel().setCaretsAndSelections(
        source.getCaretModel().getCaretsAndSelections());
    }
    catch (IllegalArgumentException ignored) {
    }
  }
}
