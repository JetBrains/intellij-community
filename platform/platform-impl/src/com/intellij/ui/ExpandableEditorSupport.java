// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

public class ExpandableEditorSupport extends ExpandableSupport<EditorTextField> {
  public ExpandableEditorSupport(@NotNull EditorTextField field) {
    super(field, null, null);
    field.addSettingsProvider(editor -> {
      initFieldEditor(editor, field.getBackground());
      updateFieldFolding(editor);
    });
  }

  public ExpandableEditorSupport(@NotNull EditorTextField field,
                                 @NotNull Function<? super String, ? extends List<String>> parser,
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
      copyCaretPosition(editor, field.getEditor(), onShow);
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
        if (editor != null) copyCaretPosition(editor, popup.getEditor(), onHide);
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

  private static void copyCaretPosition(@NotNull Editor destination, Editor source, Function<? super String, String> mapper) {
    if (source == null) return; // unexpected
    try {
      List<CaretState> states = source.getCaretModel().getCaretsAndSelections();
      if (!mapper.equals(Functions.identity())) {
        states = extractStates(mapper.fun(injectStates(source.getDocument().getText(), states)));
      }
      destination.getCaretModel().setCaretsAndSelections(states);
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  private static final Pattern TAG_PATTERN = Pattern.compile("([cse])(\\d{1,3})");

  private static List<CaretState> extractStates(String text) {
    int x = 0;
    int y = 0;
    List<LogicalPosition[]> list = new ArrayList<>();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\0') {
        int next = text.indexOf('\0', i + 1);
        if (next != -1) {
          String tag = text.substring(i + 1, next);
          Matcher matcher = TAG_PATTERN.matcher(tag);
          if (matcher.matches()) {
            String what = matcher.group(1);
            int idx = switch (what) {
              case "c" -> 0; // caret
              case "s" -> 1; // selection start
              case "e" -> 2; // selection end
              default -> throw new AssertionError("Unexpected value: " + what);
            };
            int offset = Integer.parseInt(matcher.group(2));
            while (list.size() <= offset) {
              list.add(new LogicalPosition[3]);
            }
            list.get(offset)[idx] = new LogicalPosition(y, x); 
          }
          //noinspection AssignmentToForLoopParameter
          i = next;
        }
        continue;
      }
      if (c == '\n') {
        y++;
        x = 0;
      }
      else {
        x++;
      }
    }
    return ContainerUtil.map(list, arr -> new CaretState(arr[0], arr[1], arr[2]));
  }

  private static String injectStates(String text, List<CaretState> states) {
    if (states.isEmpty()) return text;
    record Point(int x, int y) {
      static Point from(LogicalPosition pos) {
        return new Point(pos.column, pos.line);
      }
    }
    Map<Point, String> tags = new HashMap<>();
    for (int i = 0; i < states.size(); i++) {
      CaretState state = states.get(i);
      LogicalPosition caretPosition = state.getCaretPosition();
      if (caretPosition != null) {
        tags.merge(Point.from(caretPosition), "\0c" + i + "\0", String::concat);
      }
      LogicalPosition selectionStart = state.getSelectionStart();
      if (selectionStart != null) {
        tags.merge(Point.from(selectionStart), "\0s" + i + "\0", String::concat);
      }
      LogicalPosition selectionEnd = state.getSelectionEnd();
      if (selectionEnd != null) {
        tags.merge(Point.from(selectionEnd), "\0e" + i + "\0", String::concat);
      }
    }
    StringBuilder builder = new StringBuilder(text.length() + tags.values().stream().mapToInt(String::length).sum());
    int x = 0;
    int y = 0;
    for (int i = 0; i < text.length(); i++) {
      builder.append(tags.getOrDefault(new Point(x, y), ""));
      char c = text.charAt(i);
      if (c == '\n') {
        y++;
        x = 0;
      }
      else {
        x++;
      }
      builder.append(c);
    }
    return builder.toString();
  }
}
