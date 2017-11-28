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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClipboardTextPerCaretSplitter {
  @NotNull
  public List<String> split(@NotNull String input, @Nullable CaretStateTransferableData caretData, int caretCount) {
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
