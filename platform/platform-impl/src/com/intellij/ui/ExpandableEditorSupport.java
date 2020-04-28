// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import static java.util.Arrays.asList;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;

public class ExpandableEditorSupport extends ExpandableSupport<EditorTextField> {
  public ExpandableEditorSupport(@NotNull EditorTextField field) {
    super(field, null, null);
    field.addSettingsProvider(editor -> {
      initFieldEditor(editor, field.getBackground());
      updateFieldFolding(editor);
    });
  }

  public ExpandableEditorSupport(@NotNull EditorTextField field,
                                 @NotNull Function<? super String, ? extends java.util.List<String>> parser,
                                 @NotNull Function<? super List<String>, String> joiner) {
    super(field, text -> StringUtil.join(parser.fun(text), "\n"),
          text -> joiner.fun(asList(StringUtil.splitByLines(text))));
    field.addSettingsProvider(editor -> {
      initFieldEditor(editor, field.getBackground());
      updateFieldFolding(editor);
    });
  }

  protected void initPopupEditor(@NotNull EditorEx editor, Color background) {
    JLabel label = ExpandableSupport.createLabel(createCollapseExtension());
    label.setBorder(JBUI.Borders.empty(5, 3, 5, 7));
    editor.getContentComponent().putClientProperty(Expandable.class, this);
    editor.getScrollPane().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    editor.getScrollPane().getVerticalScrollBar().setBackground(background);
    editor.getScrollPane().getVerticalScrollBar().add(JBScrollBar.LEADING, label);
    editor.getScrollPane().setViewportBorder(JBUI.Borders.empty(4, 6));
    label.setOpaque(true);
  }

  protected void initFieldEditor(@NotNull EditorEx editor, Color background) {
    editor.getContentComponent().putClientProperty(Expandable.class, this);
    ExtendableTextComponent.Extension extension = createExpandExtension();
    ExtendableEditorSupport.setupExtension(editor, background, extension);
  }

  protected void updateFieldFolding(@NotNull EditorEx editor) {
    FoldingModelEx model = editor.getFoldingModel();
    CharSequence text = editor.getDocument().getCharsSequence();
    model.runBatchFoldingOperation(() -> {
      model.clearFoldRegions();
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
          model.createFoldRegion(i, i + 1, " \u23ce ", null, true);
        }
      }
    });
  }

  @NotNull
  @Override
  protected Content prepare(@NotNull EditorTextField field, @NotNull Function<? super String, String> onShow) {
    EditorTextField popup = createPopupEditor(field, onShow.fun(field.getText()));
    Color background = field.getBackground();
    popup.setBackground(background);
    popup.setOneLineMode(false);
    popup.setPreferredSize(new Dimension(field.getWidth(), 5 * field.getHeight()));
    popup.addSettingsProvider(editor -> {
      initPopupEditor(editor, background);
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
        if (editor != null) copyCaretPosition(editor, popup.getEditor());
        if (editor instanceof EditorEx) updateFieldFolding((EditorEx)editor);
      }
    };
  }

  @NotNull
  protected EditorTextField createPopupEditor(@NotNull EditorTextField field, @NotNull String text) {
    if (Objects.equals(text, field.getText())) {
      return new EditorTextField(field.getDocument(), field.getProject(), field.getFileType());
    }
    else {
      return new EditorTextField(text, field.getProject(), field.getFileType());
    }
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
