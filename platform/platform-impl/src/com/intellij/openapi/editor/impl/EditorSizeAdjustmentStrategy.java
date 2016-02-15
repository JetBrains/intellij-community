/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.Alarm;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class is aimed to help {@link EditorImpl the editor} when user extensively modifies the longest line
 * at the document (e.g. is typing at its end).
 * <p/>
 * The problem is that the longest line's width is a {@link JComponent#getPreferredSize() preferred size's width} as well.
 * So, every time width of the longest line is changed, editor's preferred size is changed too and that triggers the whole
 * component repaint.
 * <p/>
 * This component comes into the play here - it's assumed that editor notifies it every time preferred size is changed and
 * receives instructions for the further actions. For example, we can reserve additional space if we see that preferred size
 * is permanently increasing (the user is typing at the end of the longest line) etc. See method javadocs for more details.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 6/17/11 2:45 PM
 */
class EditorSizeAdjustmentStrategy {
  /**
   * Amount of time (in milliseconds) to keep information about preferred size change.
   */
  private static final long TIMING_TTL_MILLIS = 10000L;

  /**
   * Constant that indicates minimum number of preferred size changes per target amount of time that is considered to be frequent.
   */
  private static final int FREQUENT_SIZE_CHANGES_NUMBER = 10;

  /**
   * Default number of columns to reserve during frequent typing at the end of the longest document line.
   */
  private static final int DEFAULT_RESERVE_COLUMNS_NUMBER = 4;

  private final Alarm myAlarm = new Alarm();
  private final TLongArrayList myTimings = new TLongArrayList();

  private int myReserveColumns = DEFAULT_RESERVE_COLUMNS_NUMBER;
  private boolean myInsideValidation;

  /**
   * Asks to adjust new preferred size appliance if necessary.
   *
   * @param newPreferredSize newly calculated preferred size that differs from the old preferred size
   * @param oldPreferredSize old preferred size (if any)
   * @param editor           target editor
   * @return preferred size to use (given 'new preferred size' may be adjusted)
   */
  @NotNull
  Dimension adjust(@NotNull Dimension newPreferredSize, @Nullable Dimension oldPreferredSize, @NotNull EditorImpl editor) {
    if (oldPreferredSize == null || myInsideValidation) {
      return newPreferredSize;
    }
    // Process only width change.
    if (newPreferredSize.height != oldPreferredSize.height) {
      return newPreferredSize;
    }

    stripTimings();
    myTimings.add(System.currentTimeMillis());
    if (myTimings.size() < FREQUENT_SIZE_CHANGES_NUMBER) {
      return newPreferredSize;
    }

    boolean increaseWidth = newPreferredSize.width > oldPreferredSize.width;
    Dimension result;
    if (increaseWidth) {
      final int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor);
      newPreferredSize.width += myReserveColumns * spaceWidth;
      myReserveColumns += 3;
      result = newPreferredSize;
    }
    else {
      // Don't reduce preferred size on frequent reduce of the longest document line.
      result = oldPreferredSize;
    }

    scheduleSizeUpdate(editor);
    return result;
  }
  
  void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  /**
   * Removes old timings.
   */
  private void stripTimings() {
    long limit = System.currentTimeMillis() - TIMING_TTL_MILLIS;
    int endIndex = 0;
    for (; endIndex < myTimings.size(); endIndex++) {
      if (myTimings.get(endIndex) > limit) {
        break;
      }
    }
    if (endIndex > 0) {
      myTimings.remove(0, endIndex);
    }
  }

  private void scheduleSizeUpdate(@NotNull EditorImpl editor) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new UpdateSizeTask(editor), 1000);
  }

  private class UpdateSizeTask implements Runnable {
    private final EditorImpl myEditor;

    UpdateSizeTask(@NotNull EditorImpl editor) {
      myEditor = editor;
    }

    @Override
    public void run() {
      myInsideValidation = true;
      myReserveColumns = DEFAULT_RESERVE_COLUMNS_NUMBER;
      myTimings.clear();
      try {
        myEditor.validateSize();
      }
      finally {
        myInsideValidation = false;
      }
    }
  }
}
