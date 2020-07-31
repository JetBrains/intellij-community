// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@ApiStatus.Internal
abstract class DirectBufferWrapper extends ByteBufferWrapper {
  // Fixes IDEA-222358 Linux native memory leak. Please do not replace to BoundedTaskExecutor
  private static final ExecutorService ourAllocator =
    SystemInfo.isLinux && SystemProperties.getBooleanProperty("idea.limit.paged.storage.allocators", true)
    ? ConcurrencyUtil.newSingleThreadExecutor("DirectBufferWrapper allocation thread")
    : null;

  private volatile ByteBuffer myBuffer;

  DirectBufferWrapper(Path file, long offset, long length) {
    super(file, offset, length);
  }

  @Override
  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  @Override
  public ByteBuffer getBuffer() throws IOException {
    ByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = doCreate();
    }
    return buffer;
  }

  private ByteBuffer doCreate() throws IOException {
    if (ourAllocator != null) {
      // Fixes IDEA-222358 Linux native memory leak
      try {
        return ourAllocator.submit(this::create).get();
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
      return create();
    }
  }

  protected abstract ByteBuffer create() throws IOException;

  @Override
  public void release() {
    if (isDirty()) flush();
    if (myBuffer != null) {
      ByteBufferUtil.cleanBuffer(myBuffer);
      myBuffer = null;
    }
  }
}