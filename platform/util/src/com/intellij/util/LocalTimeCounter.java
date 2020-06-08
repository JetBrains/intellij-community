// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class LocalTimeCounter {
  /**
   * VirtualFile.modificationStamp is kept modulo this mask, and is compared with other stamps. Let's avoid accidental stamp inequalities
   * by normalizing all of them.
   */
  public static final int TIME_MASK = 0x00ffffff;
  private static final AtomicInteger ourCurrentTime = new AtomicInteger();

  public static long currentTime() {
    return TIME_MASK & ourCurrentTime.incrementAndGet();
  }
}