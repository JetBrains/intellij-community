/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.awt.datatransfer.DataFlavor;

public class CaretStateTransferableData implements TextBlockTransferableData {
  public static final DataFlavor FLAVOR = new DataFlavor(CaretStateTransferableData.class, "Caret state");

  public final int[] startOffsets;
  public final int[] endOffsets;

  public CaretStateTransferableData(int[] startOffsets, int[] endOffsets) {
    this.startOffsets = startOffsets;
    this.endOffsets = endOffsets;
  }

  @Override
  public DataFlavor getFlavor() {
    return FLAVOR;
  }

  @Override
  public int getOffsetCount() {
    return startOffsets.length + endOffsets.length;
  }

  @Override
  public int getOffsets(int[] offsets, int index) {
    System.arraycopy(startOffsets, 0, offsets, index, startOffsets.length);
    System.arraycopy(endOffsets, 0, offsets, index + startOffsets.length, endOffsets.length);
    return index + getOffsetCount();
  }

  @Override
  public int setOffsets(int[] offsets, int index) {
    System.arraycopy(offsets, index, startOffsets, 0, startOffsets.length);
    System.arraycopy(offsets, index + startOffsets.length, endOffsets, 0, endOffsets.length);
    return index + getOffsetCount();
  }
}
