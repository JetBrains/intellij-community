// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
class PagedFileStorageCache {
  private volatile CachedBuffer myLastBuffer;
  private volatile CachedBuffer myLastBuffer2;
  private volatile CachedBuffer myLastBuffer3;

  private static class CachedBuffer {
    private final DirectBufferWrapper myWrapper;
    private final int myLastChangeCount;
    private final long myLastPage;

    private CachedBuffer(DirectBufferWrapper wrapper, int count, long page) {
      myWrapper = wrapper;
      myLastChangeCount = count;
      myLastPage = page;
    }
  }

  void clear() {
    myLastBuffer = null;
    myLastBuffer2 = null;
    myLastBuffer3 = null;
  }

  @Nullable
  DirectBufferWrapper getPageFromCache(long page, int mappingChangeCount) {
    DirectBufferWrapper buffer;

    buffer = fromCache(myLastBuffer, page, mappingChangeCount);
    if (buffer != null) return buffer;

    buffer = fromCache(myLastBuffer2, page, mappingChangeCount);
    if (buffer != null) return buffer;

    buffer = fromCache(myLastBuffer3, page, mappingChangeCount);
    return buffer;
  }

  @Nullable
  private static DirectBufferWrapper fromCache(CachedBuffer lastBuffer, long page, int mappingChangeCount) {
    if (lastBuffer != null && !lastBuffer.myWrapper.isReleased() &&
        mappingChangeCount == lastBuffer.myLastChangeCount &&
        lastBuffer.myLastPage == page) {
      return lastBuffer.myWrapper;
    }
    return null;
  }

  /* race */ void updateCache(long page, DirectBufferWrapper byteBufferWrapper, int mappingChangeCount) {
    if (myLastBuffer != null && myLastBuffer.myLastPage != page) {
      myLastBuffer3 = myLastBuffer2;
      myLastBuffer2 = myLastBuffer;
    }
    myLastBuffer = new CachedBuffer(byteBufferWrapper, mappingChangeCount, page);
  }
}
