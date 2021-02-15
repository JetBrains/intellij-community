// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jna;

import com.sun.jna.Memory;

/**
 * Explicitly disposable native memory block.
 */
public class DisposableMemory extends Memory implements AutoCloseable {
  public DisposableMemory(long size) {
    super(size);
  }

  @Override
  public void close() {
    super.dispose();
  }
}
