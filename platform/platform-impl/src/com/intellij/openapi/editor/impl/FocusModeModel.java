// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE;
import static com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE;

public class FocusModeModel {
  public static final Key<List<RangeMarker>> FOCUS_MODE_RANGES = Key.create("focus.mode.ranges");
  public static final Key<TextAttributes> FOCUS_MODE_ATTRIBUTES = Key.create("editor.focus.mode.attributes");
  public static final int LAYER = 10_000;

  private final List<RangeHighlighter> myFocusModeMarkup = ContainerUtil.newSmartList();
  @NotNull private final EditorImpl myEditor;
  private RangeMarker myFocusModeRange;

  public FocusModeModel(@NotNull EditorImpl editor) {
    myEditor = editor;

    myEditor.getScrollingModel().addVisibleAreaListener(e -> {
      AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
      if (event instanceof MouseEvent && !EditorUtil.isPrimaryCaretVisible(myEditor)) {
        clearFocusMode(); // clear when scrolling with touchpad or mouse and primary caret is out the visible area
      }
      else {
        myEditor.applyFocusMode(); // apply the focus mode when jumping to the next line, e.g. Cmd+G
      }
    });

    CaretModelImpl caretModel = myEditor.getCaretModel();
    caretModel.addCaretListener(new CaretListener() {
      @Override
      public void caretAdded(@NotNull CaretEvent event) {
        process(event);
      }

      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        process(event);
      }

      @Override
      public void caretRemoved(@NotNull CaretEvent event) {
        process(event);
      }

      private void process(@NotNull CaretEvent event) {
        Caret caret = event.getCaret();
        if (caret == caretModel.getPrimaryCaret()) {
          applyFocusMode(caret);
        }
      }
    });

    myEditor.getSelectionModel().addSelectionListener(new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionEvent e) {
        myEditor.applyFocusMode();
      }
    });
  }

  public RangeMarker getFocusModeRange() {
    return myFocusModeRange;
  }

  public void applyFocusMode(@NotNull Caret caret) {
    List<RangeMarker> data = myEditor.getUserData(FOCUS_MODE_RANGES);
    clearFocusMode();
    RangeMarker startMarker = findFocusMarker(caret.getSelectionStart(), data);
    if (startMarker != null) {
      RangeMarker endMarker = findFocusMarker(caret.getSelectionEnd(), data);
      applyFocusMode(
        enlargeFocusRangeIfNeeded(endMarker == null ? startMarker : new TextRange(startMarker.getStartOffset(), endMarker.getEndOffset())));
    }
  }

  public void clearFocusMode() {
    myFocusModeMarkup.forEach(myEditor.getMarkupModel()::removeHighlighter);
    myFocusModeMarkup.clear();
    if (myFocusModeRange != null) {
      myFocusModeRange.dispose();
      myFocusModeRange = null;
    }
  }

  public boolean isInFocusMode(@NotNull RangeMarker region) {
    return myFocusModeRange != null && !intersects(myFocusModeRange, region);
  }

  @NotNull
  private Segment enlargeFocusRangeIfNeeded(Segment range) {
    int originalStart = range.getStartOffset();
    DocumentEx document = myEditor.getDocument();
    int start = DocumentUtil.getLineStartOffset(originalStart, document);
    if (start < originalStart) {
      range = new TextRange(start, range.getEndOffset());
    }
    int originalEnd = range.getEndOffset();
    int end = DocumentUtil.getLineEndOffset(originalEnd, document);
    if (end >= originalEnd) {
      range = new TextRange(range.getStartOffset(), end < document.getTextLength() ? end + 1 : end);
    }
    return range;
  }

  private void applyFocusMode(@NotNull Segment focusRange) {
    EditorColorsScheme scheme = ObjectUtils.notNull(myEditor.getColorsScheme(), EditorColorsManager.getInstance().getGlobalScheme());
    Color background = scheme.getDefaultBackground();
    //noinspection UseJBColor
    Color foreground = Registry.getColor(ColorUtil.isDark(background) ?
                                         "editor.focus.mode.color.dark" :
                                         "editor.focus.mode.color.light", Color.GRAY);
    TextAttributes attributes = new TextAttributes(foreground, background, background, LINE_UNDERSCORE, Font.PLAIN);
    myEditor.putUserData(FOCUS_MODE_ATTRIBUTES, attributes);

    MarkupModel markupModel = myEditor.getMarkupModel();
    DocumentEx document = myEditor.getDocument();
    int textLength = document.getTextLength();

    int start = focusRange.getStartOffset();
    int end = focusRange.getEndOffset();

    if (start <= textLength) myFocusModeMarkup.add(markupModel.addRangeHighlighter(0, start, LAYER, attributes, EXACT_RANGE));
    if (end <= textLength) myFocusModeMarkup.add(markupModel.addRangeHighlighter(end, textLength, LAYER, attributes, EXACT_RANGE));

    myFocusModeRange = document.createRangeMarker(start, end);
  }

  @Nullable
  private static RangeMarker findFocusMarker(int offset, @Nullable List<RangeMarker> data) {
    if (data == null) return null;
    for (RangeMarker marker : data) {
      if (marker.getStartOffset() <= offset && offset <= marker.getEndOffset()) {
        return marker;
      }
    }
    return null;
  }

  private static boolean intersects(RangeMarker a, RangeMarker b) {
    return Math.max(a.getStartOffset(), b.getStartOffset()) < Math.min(a.getEndOffset(), b.getEndOffset());
  }
}
