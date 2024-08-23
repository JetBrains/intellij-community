// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.util;

public class StartEndPair {
  public final int start;
  public final int end;
  public StartEndPair(int start, int end) {
    this.start = start;
    this.end = end;
  }

  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof StartEndPair)) return false;
    return ((StartEndPair)obj).start == start && ((StartEndPair)obj).end == end;
  }

  @Override
  public int hashCode() {
    return start * 31 + end;
  }

  @Override
  public String toString() {
    return String.format("%d->%d",start,end);
  }

  public static StartEndPair join(StartEndPair... pairs) {
    int start = Integer.MAX_VALUE;
    int end = Integer.MIN_VALUE;
    for (StartEndPair pair : pairs) {
        if (pair == null) continue;
        start = Math.min(start, pair.start);
        end = Math.max(end, pair.end);
    }
    return new StartEndPair(start, end);
  }
}
