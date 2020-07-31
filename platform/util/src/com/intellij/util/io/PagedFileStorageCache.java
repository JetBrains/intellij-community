// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

@ApiStatus.Internal
class PagedFileStorageCache {
  private static final int UNKNOWN_PAGE = -1;

  private int myLastPage = UNKNOWN_PAGE;
  private int myLastPage2 = UNKNOWN_PAGE;
  private int myLastPage3 = UNKNOWN_PAGE;
  private ByteBufferWrapper myLastBuffer;
  private ByteBufferWrapper myLastBuffer2;
  private ByteBufferWrapper myLastBuffer3;
  private int myLastChangeCount;
  private int myLastChangeCount2;
  private int myLastChangeCount3;
  
  synchronized void clear() {
    myLastPage = UNKNOWN_PAGE;
    myLastPage2 = UNKNOWN_PAGE;
    myLastPage3 = UNKNOWN_PAGE;
    myLastBuffer = null;
    myLastBuffer2 = null;
    myLastBuffer3 = null;
  }

  @Nullable
  synchronized ByteBufferWrapper getPageFromCache(long page, int mappingChangeCount, boolean readOnly) {
    if (myLastPage == page) {
      ByteBuffer buf = myLastBuffer.getCachedBuffer();
      if (buf != null && myLastChangeCount == mappingChangeCount) {
        return myLastBuffer;
      }
    } else if (myLastPage2 == page) {
      ByteBuffer buf = myLastBuffer2.getCachedBuffer();
      if (buf != null && myLastChangeCount2 == mappingChangeCount) {
        return myLastBuffer2;
      }
    } else if (myLastPage3 == page) {
      ByteBuffer buf = myLastBuffer3.getCachedBuffer();
      if (buf != null && myLastChangeCount3 == mappingChangeCount) {
        return myLastBuffer3;
      }
    }
    return null;
  }

  synchronized void updateCache(long page, ByteBufferWrapper byteBufferWrapper, int mappingChangeCount) {
    if (myLastPage != page) {
      myLastPage3 = myLastPage2;
      myLastBuffer3 = myLastBuffer2;
      myLastChangeCount3 = myLastChangeCount2;

      myLastPage2 = myLastPage;
      myLastBuffer2 = myLastBuffer;
      myLastChangeCount2 = myLastChangeCount;

      myLastBuffer = byteBufferWrapper;
      myLastPage = (int)page; // TODO long page
    }
    else {
      myLastBuffer = byteBufferWrapper;
    }

    myLastChangeCount = mappingChangeCount;
  }
}
