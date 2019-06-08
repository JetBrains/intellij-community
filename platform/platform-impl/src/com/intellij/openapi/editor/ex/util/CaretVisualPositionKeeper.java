/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollingModel;
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
public class CaretVisualPositionKeeper {
  private final Map<Editor, Integer> myCaretRelativeVerticalPositions = new HashMap<>();

  public CaretVisualPositionKeeper(@Nullable Editor editor) {
    this(editor == null ? Editor.EMPTY_ARRAY : new Editor[]{editor});
  }

  public CaretVisualPositionKeeper(@Nullable Document document) {
    this(document == null ? Editor.EMPTY_ARRAY : EditorFactory.getInstance().getEditors(document));
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
