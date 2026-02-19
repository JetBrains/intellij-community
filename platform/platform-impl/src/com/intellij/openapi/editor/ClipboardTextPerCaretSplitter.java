// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClipboardTextPerCaretSplitter {
  public @NotNull List<String> split(@NotNull String input, @Nullable CaretStateTransferableData caretData, int caretCount) {
    if (caretCount <= 0) {
      throw new IllegalArgumentException("Caret count must be positive");
    }
    if (caretCount == 1) {
      return Collections.singletonList(input);
    }
    //
    // | caretData |  input split by   | split count | Nth caret content |
    // |:----------|:-----------------:|:-----------:|:------------------|
    // | null      |        \n         |      1      | split[0]          |
    // | null      |        \n         |    != 1     | split[N]          |
    // | !null     | caretData.offsets |      1      | split[0]          |
    // | !null     | caretData.offsets |    != 1     | split[N]          |
    //
    List<String> result = new ArrayList<>(caretCount);
    if (caretData == null) {
      String[] lines = input.split("\n", -1);
      // need to ignore the trailing \n in input for purpose of having a 1 sourceCaret, and makes no difference for non 1 sourceCaret count
      int sourceCaretCount = lines.length == 2 && lines[1].isEmpty() ? 1 : lines.length;
      for (int i = 0; i < caretCount; i++) {
        if (sourceCaretCount == 1) {
          result.add(lines[0]);
        }
        else {
          result.add(i < lines.length ? lines[i] : "");
        }
      }
    }
    else {
      int sourceCaretCount = caretData.startOffsets.length;
      for (int i = 0; i < caretCount; i++) {
        if (sourceCaretCount == 1) {
          result.add(input);
        }
        else {
          result.add(i < sourceCaretCount ? input.substring(caretData.startOffsets[i], caretData.endOffsets[i]) : "");
        }
      }
    }
    return result;
  }
}
