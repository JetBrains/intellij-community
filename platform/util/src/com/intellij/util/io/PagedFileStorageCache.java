// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Small (max 3 items) per-{@link PagedFileStorage} MRU cache of pages ({@link DirectBufferWrapper}).
 * Used to avoid relatively expensive (and guarded by global locks) access to {@link FilePageCache}
 * for the most recent pages -- which are very likely to be accessed more than once.
 */
@ApiStatus.Internal
final class PagedFileStorageCache {
  private volatile CachedBuffer myLastBuffer;
  private volatile CachedBuffer myLastBuffer2;
  private volatile CachedBuffer myLastBuffer3;

  private static final class CachedBuffer {
    private final DirectBufferWrapper myWrapper;
    private final long myLastPage;

    private CachedBuffer(DirectBufferWrapper wrapper, long page) {
      myWrapper = wrapper;
      myLastPage = page;
    }
  }

  void clear() {
    myLastBuffer = null;
    myLastBuffer2 = null;
    myLastBuffer3 = null;
  }

  @Nullable
  DirectBufferWrapper getPageFromCache(long page) {
    DirectBufferWrapper buffer;

    buffer = fromCache(myLastBuffer, page);
    if (buffer != null) return buffer;

    buffer = fromCache(myLastBuffer2, page);
    if (buffer != null) return buffer;

    buffer = fromCache(myLastBuffer3, page);
    return buffer;
  }

  private static @Nullable DirectBufferWrapper fromCache(CachedBuffer lastBuffer, long page) {
    if (lastBuffer != null && !lastBuffer.myWrapper.isReleased() &&
        lastBuffer.myLastPage == page) {
      return lastBuffer.myWrapper;
    }
    return null;
  }

  /* race */ void updateCache(long page, DirectBufferWrapper byteBufferWrapper) {
    if (myLastBuffer != null && myLastBuffer.myLastPage != page) {
      myLastBuffer3 = myLastBuffer2;
      myLastBuffer2 = myLastBuffer;
    }
    myLastBuffer = new CachedBuffer(byteBufferWrapper, page);
  }
}
