// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Allows to keep vertical scrolling position in editor for operations that affect editor contents (changing document, collapsing/expanding
 * folding regions, etc). If caret is in view, then its vertical position is preserved, otherwise, the code displayed in top left corner of
 * editor viewport. {@link #savePosition()} method should be called before the operation, to save scrolling position, and
 * {@link #restorePosition(boolean)} method - after the operation, to restore the position.
 */
public final class EditorScrollingPositionKeeper implements Disposable {
  private final Editor myEditor;
  private int myViewportShift;
  private RangeMarker myTopLeftCornerMarker;

  public EditorScrollingPositionKeeper(@NotNull Editor editor) {
    myEditor = editor;
  }

  public void savePosition() {
    if (myEditor instanceof ImaginaryEditor) return;

    disposeMarker();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    int caretY = myEditor.visualLineToY(myEditor.getCaretModel().getVisualPosition().line);
    if (visibleArea.height > 0 && (caretY + myEditor.getLineHeight() <= visibleArea.y || caretY >= (visibleArea.y + visibleArea.height))) {
      int topLeftCornerOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(visibleArea.getLocation()));
      myTopLeftCornerMarker = myEditor.getDocument().createRangeMarker(topLeftCornerOffset, topLeftCornerOffset);
      myViewportShift = myEditor.offsetToXY(topLeftCornerOffset).y - visibleArea.y;
    }
    else {
      myTopLeftCornerMarker = null;
      myViewportShift = caretY - visibleArea.y;
    }
  }

  public void restorePosition(boolean stopAnimation) {
    if (myEditor instanceof ImaginaryEditor) return;

    int newY;
    if (myTopLeftCornerMarker == null) {
      newY = myEditor.visualLineToY(myEditor.getCaretModel().getVisualPosition().line);
    }
    else {
      if (!myTopLeftCornerMarker.isValid()) return;
      newY = myEditor.offsetToXY(myTopLeftCornerMarker.getStartOffset()).y;
    }
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    Rectangle targetArea = scrollingModel.getVisibleAreaOnScrollingFinished();
    // when animated scrolling is in progress, we'll not stop it immediately
    boolean disableAnimation = targetArea.equals(scrollingModel.getVisibleArea()) || stopAnimation;
    if (disableAnimation) scrollingModel.disableAnimation();
    scrollingModel.scroll(targetArea.x, newY - myViewportShift); // can't use 'scrollVertically' - it aborts horizontal scrolling
    if (disableAnimation) scrollingModel.enableAnimation();
  }


  @Override
  public void dispose() {
    disposeMarker();
  }

  private void disposeMarker() {
    if (myTopLeftCornerMarker != null) myTopLeftCornerMarker.dispose();
  }

  /**
   * Performs given operation, restoring editor scrolling position afterwards.
   */
  public static void perform(@Nullable Editor editor, boolean stopAnimation, @NotNull Runnable operation) {
    if (editor == null || editor instanceof ImaginaryEditor) {
      operation.run();
      return;
    }
    EditorScrollingPositionKeeper keeper = new EditorScrollingPositionKeeper(editor);
    keeper.savePosition();
    try {
      operation.run();
      keeper.restorePosition(stopAnimation);
    }
    finally {
      Disposer.dispose(keeper);
    }
  }

  /**
   * Performs given operation, restoring scrolling position in all document's editors afterwards.
   */
  public static void perform(@Nullable Document document, boolean stopAnimation, @NotNull Runnable runnable) {
    EditorScrollingPositionKeeper.ForDocument keeper = new EditorScrollingPositionKeeper.ForDocument(document);
    keeper.savePosition();
    try {
      runnable.run();
      keeper.restorePosition(stopAnimation);
    }
    finally {
      Disposer.dispose(keeper);
    }
  }

  /**
   * Same as {@link EditorScrollingPositionKeeper}, but tracking all editors for a given document.
   */
  public static final class ForDocument implements Disposable {
    private final List<EditorScrollingPositionKeeper> myKeepers;

    public ForDocument(@Nullable Document document) {
      if (document == null) {
        myKeepers = Collections.emptyList();
      }
      else {
        myKeepers = EditorFactory.getInstance().editors(document).map(EditorScrollingPositionKeeper::new).collect(Collectors.toList());
      }
    }

    public void savePosition() {
      myKeepers.forEach(EditorScrollingPositionKeeper::savePosition);
    }

    public void restorePosition(boolean stopAnimation) {
      myKeepers.forEach(k -> k.restorePosition(stopAnimation));
    }

    @Override
    public void dispose() {
      myKeepers.forEach(Disposer::dispose);
    }
  }
}
