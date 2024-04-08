// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Range;

public interface MemorySizeAware {
  @Range(from = 0, to = Long.MAX_VALUE)
  long getMemorySize();
}
