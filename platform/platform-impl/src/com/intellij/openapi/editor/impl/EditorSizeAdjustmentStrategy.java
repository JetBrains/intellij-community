// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SingleEdtTaskScheduler;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class is aimed to help {@linkplain EditorImpl the editor} when the user extensively modifies the longest line
 * at the document (e.g., is typing at its end).
 * <p>
 * The problem is that the longest line's width determines the width of {@link JComponent#getPreferredSize()}.
 * So, every time the width of the longest line is changed,
 * the editor's preferred size is changed too and that triggers the whole component repaint.
 * <p>
 * This component comes into play here - it's assumed that the editor notifies it every time the preferred size is changed and
 * receives instructions for the further actions. For example, we can reserve additional space if we see that the preferred size
 * is permanently increasing (the user is typing at the end of the longest line), etc.
 * <p>
 * Not thread-safe.
 */
final class EditorSizeAdjustmentStrategy {
  /**
   * Amount of time (in milliseconds) to keep information about preferred size change.
   */
  private static final long TIMING_TTL_MILLIS = 10000L;

  /**
   * The minimum number of preferred size changes per target amount of time that is considered to be frequent.
   */
  private static final int FREQUENT_SIZE_CHANGES_NUMBER = getFrequentSizeChangesNumber();

  /**
   * Default number of columns to reserve during frequent typing at the end of the longest document line.
   */
  private static final int DEFAULT_RESERVE_COLUMNS_NUMBER = 4;

  private final SingleEdtTaskScheduler alarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private final LongList myTimings = new LongArrayList();

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
      // don't reduce preferred size on frequent reduction of the longest document line.
      result = oldPreferredSize;
    }

    scheduleSizeUpdate(editor);
    return result;
  }

  void cancelAllRequests() {
    alarm.cancel();
  }

  /**
   * Removes old timings.
   */
  private void stripTimings() {
    long limit = System.currentTimeMillis() - TIMING_TTL_MILLIS;
    int endIndex = 0;
    for (; endIndex < myTimings.size(); endIndex++) {
      if (myTimings.getLong(endIndex) > limit) {
        break;
      }
    }
    if (endIndex > 0) {
      myTimings.removeElements(0, endIndex);
    }
  }

  private void scheduleSizeUpdate(@NotNull EditorImpl editor) {
    alarm.cancelAndRequest(1000, new UpdateSizeTask(editor));
  }

  /**
   * It is a hot fix for IJPL-155997 (editor does not scroll horizontally while typing).
   * Since it is a pretty annoying bug that you cannot see chars you just typed the freq value is increased to {@code 200}.
   * The old value is {@code 10}.
   * A proper fix should be implemented in the future.
   */
  private static int getFrequentSizeChangesNumber() {
    return Registry.intValue("editor.viewport.width.update.frequency", 200);
  }

  private final class UpdateSizeTask implements Runnable {
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
