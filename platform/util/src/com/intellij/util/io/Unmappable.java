// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import java.io.IOException;

/**
 * Interface for a storages that utilise memory-mapped files.
 * <p/>
 * The only safe way to deal with mmapped files in java is to let GC do the unmapping -- since this is the
 * only way to guarantee no-use-after-unmap.
 * <p/>
 * But there are cases there 'eager', explicit unmapping is hard to avoid: e.g., on Windows mapped file
 * can't be deleted, so one needs to unmap the storage just to clean temporary folder in unit-test.
 * <p/>
 * But unmap is anyway an unsafe option, it should be used with caution. This is why the strategy to work
 * with mmapped files is following:
 * 1. MMapped storages provide regular .close() ({@link AutoCloseable}/{@link java.io.Closeable}) method,
 * that does NOT unmap the buffers, but just clears the references to mapped buffers, and relies on GC for
 * actual unmapping.
 * 2. MMapped storages implement this interface, and whoever needs explicit unmapping -- cast storage to
 * {@link Unmappable}. This way it is explicit that the operation is unsafe
 */
public interface Unmappable {
  /**
   * Close the storage, and unmap all the memory-mapped buffers used.
   * BEWARE: explicit buffer unmapping is unsafe, since any use of mapped buffer after unmapping leads to a JVM crash.
   * Because of that, this method is inherently risky.
   * 'Safe' use of this method requires _stop all the uses_ of this storage beforehand -- ideally, it should be no
   * reference to mapped ByteBuffer alive/in use by any thread, i.e., no chance any thread accesses any mapped buffer
   * of this storage after _starting_ the invocation of this method.
   * Generally, it is much safer to call {@link #close()} without unmap -- in which case JVM/GC is responsible to
   * keep buffers mapped until at least someone uses them.
   */
  void closeAndUnsafelyUnmap() throws IOException;
}
