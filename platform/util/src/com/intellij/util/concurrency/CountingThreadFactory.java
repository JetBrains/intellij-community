// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class CountingThreadFactory implements ThreadFactory {
  protected final AtomicInteger counter = new AtomicInteger();

  public int getCount() {
    return counter.get();
  }
}
