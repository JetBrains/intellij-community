// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows to keep caret's position in editor window at the same vertical position for operations, that can potentially move caret.
 * An instance of this class is to be created before the operation, method {@link #restoreOriginalLocation(boolean)} should be called after it.
 *
 * @deprecated Use {@link EditorScrollingPositionKeeper} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public class CaretVisualPositionKeeper {
  private final Map<Editor, Integer> myCaretRelativeVerticalPositions = new HashMap<>();

  public CaretVisualPositionKeeper(@Nullable Editor editor) {
    this(editor == null ? Editor.EMPTY_ARRAY : new Editor[]{editor});
  }

  private CaretVisualPositionKeeper(Editor[] editors) {
    for (Editor editor : editors) {
      Rectangle visibleArea = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
      Point pos = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
      int relativePosition = pos.y - visibleArea.y;
      myCaretRelativeVerticalPositions.put(editor, relativePosition);
    }
  }

  public void restoreOriginalLocation(boolean stopAnimation) {
    for (Map.Entry<Editor, Integer> e : myCaretRelativeVerticalPositions.entrySet()) {
      Editor editor = e.getKey();
      int relativePosition = e.getValue();
      Point caretLocation = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
      int scrollOffset = caretLocation.y - relativePosition;
      ScrollingModel scrollingModel = editor.getScrollingModel();
      Rectangle targetArea = scrollingModel.getVisibleAreaOnScrollingFinished();
      // when animated scrolling is in progress, we'll not stop it immediately
      boolean disableAnimation = targetArea.equals(scrollingModel.getVisibleArea()) || stopAnimation;
      if (disableAnimation) scrollingModel.disableAnimation();
      scrollingModel.scroll(targetArea.x, scrollOffset);
      if (disableAnimation) scrollingModel.enableAnimation();
    }
  }
}
