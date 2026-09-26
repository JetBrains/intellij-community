// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generator for assigning 'stamp' values in VirtualFile.getModificationStamp() and Document.getModificationStamp()
 * Has nothing to do with 'time' -- it's just an atomic counter.
 */
public final class LocalTimeCounter {
  //TODO RC: rename to FileModificationStampGenerator? 'local time' is misleading here.

  /**
   * VirtualFile.modificationStamp is kept modulo this mask, and is compared with other stamps.
   * Let's avoid accidental stamp inequalities by normalizing all of them.
   * @see com.intellij.openapi.vfs.newvfs.impl.VfsData.Segment#getModificationStamp(int)
   */
  @ApiStatus.Internal
  public static final int MOD_COUNTER_MASK = 0x00ff_ffff;

  private static final AtomicInteger globalModCounter = new AtomicInteger();

  /** @return next value for VFS modification stamp */
  public static long currentTime() {
    return MOD_COUNTER_MASK & globalModCounter.incrementAndGet();
  }
}