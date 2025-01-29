// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE;
import static com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE;

public final class FocusModeModel implements Disposable {
  public static final Key<TextAttributes> FOCUS_MODE_ATTRIBUTES = Key.create("editor.focus.mode.attributes");
  private static final int LAYER = 10_000;

  private final List<RangeHighlighter> myFocusModeMarkup = new SmartList<>();
  private final @NotNull EditorImpl myEditor;
  private RangeMarker myFocusModeRange;

  private final List<FocusModeModelListener> mySegmentListeners = new SmartList<>();
  private final RangeMarkerTree<RangeMarkerEx> myFocusMarkerTree;

  @ApiStatus.Internal
  public FocusModeModel(@NotNull EditorImpl editor) {
    myEditor = editor;
    myFocusMarkerTree = new RangeMarkerTree<>(editor.getDocument());

    myEditor.getScrollingModel().addVisibleAreaListener(e -> ReadAction.run(() -> {
      AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
      if (event instanceof MouseEvent && !EditorUtil.isPrimaryCaretVisible(myEditor)) {
        clearFocusMode(); // clear when scrolling with touchpad or mouse and primary caret is out the visible area
      }
      else {
        myEditor.applyFocusMode(); // apply the focus mode when jumping to the next line, e.g. Cmd+G
      }
    }));

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

  RangeMarker getFocusModeRange() {
    return myFocusModeRange;
  }

  void applyFocusMode(@NotNull Caret caret) {
    // Focus mode should not be applied when idea is used as rd server (for example, centaur mode).
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    RangeMarkerEx[] startRange = new RangeMarkerEx[1];
    RangeMarkerEx[] endRange = new RangeMarkerEx[1];
    myFocusMarkerTree.processContaining(caret.getSelectionStart(), startMarker -> {
      if (startRange[0] == null || startRange[0].getStartOffset() < startMarker.getStartOffset()) {
        startRange[0] = startMarker;
      }
      return true;
    });
    myFocusMarkerTree.processContaining(caret.getSelectionEnd(), endMarker -> {
      if (endRange[0] == null || endRange[0].getEndOffset() > endMarker.getEndOffset()) {
        endRange[0] = endMarker;
      }
      return true;
    });

    clearFocusMode();
    if (startRange[0] != null && endRange[0] != null) {
      applyFocusMode(enlargeFocusRangeIfNeeded(new TextRange(startRange[0].getStartOffset(), endRange[0].getEndOffset())));
    }
  }

  void clearFocusMode() {
    myFocusModeMarkup.forEach(myEditor.getMarkupModel()::removeHighlighter);
    myFocusModeMarkup.clear();
    if (myFocusModeRange != null) {
      myFocusModeRange.dispose();
      myFocusModeRange = null;
    }
  }

  @ApiStatus.Internal
  public boolean isInFocusMode(@NotNull RangeMarker region) {
    return myFocusModeRange != null && !intersects(myFocusModeRange, region);
  }

  @ApiStatus.Internal
  public @NotNull RangeMarker createFocusRegion(int start, int end) {
    RangeMarkerEx marker = new RangeMarkerImpl(myEditor.getDocument(), start, end, false, false);
    myFocusMarkerTree.addInterval(marker, start, end, false, false, true, 0);
    mySegmentListeners.forEach(l -> l.focusRegionAdded(marker));
    return marker;
  }

  @ApiStatus.Internal
  public @Nullable RangeMarker findFocusRegion(int start, int end) {
    RangeMarker[] found = new RangeMarker[1];
    myFocusMarkerTree.processOverlappingWith(start, end, range -> {
      if (range.getStartOffset() == start && range.getEndOffset() == end) {
        found[0] = range;
        return false;
      }
      return true;
    });
    return found[0];
  }

  @ApiStatus.Internal
  public void removeFocusRegion(@NotNull RangeMarker marker) {
    boolean removed = myFocusMarkerTree.removeInterval((RangeMarkerEx)marker);
    if (removed) mySegmentListeners.forEach(l -> l.focusRegionRemoved(marker));
  }

  @ApiStatus.Internal
  public void addFocusSegmentListener(FocusModeModelListener newListener, Disposable disposable) {
    mySegmentListeners.add(newListener);
    Disposer.register(disposable, () -> mySegmentListeners.remove(newListener));
  }

  private @NotNull Segment enlargeFocusRangeIfNeeded(@NotNull Segment range) {
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

  @Override
  public void dispose() {
    myFocusMarkerTree.dispose(myEditor.getDocument());
  }

  private static boolean intersects(RangeMarker a, RangeMarker b) {
    return Math.max(a.getStartOffset(), b.getStartOffset()) < Math.min(a.getEndOffset(), b.getEndOffset());
  }

  @ApiStatus.Internal
  public interface FocusModeModelListener {
    void focusRegionAdded(@NotNull Segment newRegion);
    void focusRegionRemoved(@NotNull Segment oldRegion);
  }
}
