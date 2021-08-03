// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;

import java.util.Comparator;

public interface Segment {
  Segment[] EMPTY_ARRAY = new Segment[0];

  @Contract(pure = true)
  int getStartOffset();

  @Contract(pure = true)
  int getEndOffset();

  Comparator<Segment> BY_START_OFFSET_THEN_END_OFFSET =
    Comparator.comparingInt(Segment::getStartOffset).thenComparingInt(Segment::getEndOffset);
}
