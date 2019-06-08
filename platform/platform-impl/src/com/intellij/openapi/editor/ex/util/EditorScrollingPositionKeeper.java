// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;


/**
 * Allows to keep vertical scrolling position in editor for operations that affect editor contents (changing document, collapsing/expanding
 * folding regions, etc). If caret is in view, then its vertical position is preserved, otherwise, the code displayed in top left corner of
 * editor viewport. {@link #savePosition()} method should be called before the operation, to save scrolling position, and
 * {@link #restorePosition(boolean)} method - after the operation, to restore the position.
 */
public class EditorScrollingPositionKeeper implements Disposable {
  private final Editor myEditor;
  private int myViewportShift;
  private RangeMarker myTopLeftCornerMarker;

  public EditorScrollingPositionKeeper(@NotNull Editor editor) {
    myEditor = editor;
  }

  public void savePosition() {
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
    int newY;
    if (myTopLeftCornerMarker == null) {
      newY = myEditor.visualLineToY(myEditor.getCaretModel().getVisualPosition().line);
    }
    else {
      if (!myTopLeftCornerMarker.isValid()) return;
      newY = myEditor.offsetToXY(myTopLeftCornerMarker.getStartOffset()).y;
    }
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    // when animated scrolling is in progress, we'll not stop it immediately
    boolean disableAnimation = scrollingModel.getVisibleAreaOnScrollingFinished().equals(scrollingModel.getVisibleArea()) || stopAnimation;
    if (disableAnimation) scrollingModel.disableAnimation();
    scrollingModel.scrollVertically(newY - myViewportShift);
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
    if (editor == null) {
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
  public static class ForDocument implements Disposable {
    private final List<EditorScrollingPositionKeeper> myKeepers;

    public ForDocument(@Nullable Document document) {
      myKeepers = document == null ? Collections.emptyList()
                                   : ContainerUtil.map(EditorFactory.getInstance().getEditors(document),
                                                       EditorScrollingPositionKeeper::new);
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
