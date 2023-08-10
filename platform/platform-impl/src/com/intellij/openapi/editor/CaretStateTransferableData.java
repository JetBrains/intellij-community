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

  @Nullable
  public static CaretStateTransferableData getFrom(Transferable t) {
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
