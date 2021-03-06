// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ConcurrencyUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

final class DirectByteBufferAllocator {
  // Fixes IDEA-222358 Linux native memory leak. Please do not replace to BoundedTaskExecutor
  private static final ExecutorService ourAllocator =
    SystemInfoRt.isLinux && Boolean.parseBoolean(System.getProperty("idea.limit.paged.storage.allocators", "true"))
    ? ConcurrencyUtil.newSingleThreadExecutor("DirectBufferWrapper allocation thread")
    : null;


  static ByteBuffer allocate(ThrowableComputable<ByteBuffer, IOException> computable) throws IOException {
    if (ourAllocator != null) {
      // Fixes IDEA-222358 Linux native memory leak
      try {
        return ourAllocator.submit(computable::compute).get();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
          throw (IOException)cause;
        }
        else if (cause instanceof OutOfMemoryError) {
          throw (OutOfMemoryError)cause; // OutOfMemoryError should be propagated (handled above)
        }
        else {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      return computable.compute();
    }
  }
}
