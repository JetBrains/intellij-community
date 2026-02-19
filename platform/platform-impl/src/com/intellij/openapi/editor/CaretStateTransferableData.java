// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;

public class CaretStateTransferableData implements TextBlockTransferableData {
  private static final Logger LOG = Logger.getInstance(CaretStateTransferableData.class);

  public static final DataFlavor FLAVOR = new DataFlavor(CaretStateTransferableData.class, "Caret state");

  public final int[] startOffsets;
  public final int[] endOffsets;

  public CaretStateTransferableData(int @NotNull [] startOffsets, int @NotNull [] endOffsets) {
    this.startOffsets = startOffsets;
    this.endOffsets = endOffsets;
  }

  @Override
  public @Nullable DataFlavor getFlavor() {
    return FLAVOR;
  }

  @Override
  public int getOffsetCount() {
    return startOffsets.length + endOffsets.length;
  }

  @Override
  public int getOffsets(int @NotNull [] offsets, int index) {
    System.arraycopy(startOffsets, 0, offsets, index, startOffsets.length);
    System.arraycopy(endOffsets, 0, offsets, index + startOffsets.length, endOffsets.length);
    return index + getOffsetCount();
  }

  @Override
  public int setOffsets(int @NotNull [] offsets, int index) {
    System.arraycopy(offsets, index, startOffsets, 0, startOffsets.length);
    System.arraycopy(offsets, index + startOffsets.length, endOffsets, 0, endOffsets.length);
    return index + getOffsetCount();
  }

  public int getCaretCount() {
    return startOffsets.length;
  }

  public static @Nullable CaretStateTransferableData getFrom(Transferable t) {
    try {
      return t.isDataFlavorSupported(FLAVOR) ? (CaretStateTransferableData)t.getTransferData(FLAVOR) : null;
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return null;
  }

  public static boolean areEquivalent(@Nullable CaretStateTransferableData d1, @Nullable CaretStateTransferableData d2) {
    return  (d1 == null || d1.getCaretCount() == 1) && (d2 == null || d2.getCaretCount() == 1) ||
            d1 != null && d2 != null && Arrays.equals(d1.startOffsets, d2.startOffsets) && Arrays.equals(d1.endOffsets, d2.endOffsets);
  }
}
