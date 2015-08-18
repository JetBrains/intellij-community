/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.util.SystemProperties;
import com.intellij.util.containers.LimitedPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class Page {
  public static final int PAGE_SIZE = SystemProperties.getIntProperty("idea.io.page.size", 8 * 1024);

  private static final LimitedPool<ByteBuffer> ourBufferPool = new LimitedPool<ByteBuffer>(10, new LimitedPool.ObjectFactory<ByteBuffer>() {
    @NotNull
    @Override
    public ByteBuffer create() {
      return ByteBuffer.allocate(PAGE_SIZE);
    }

    @Override
    public void cleanup(@NotNull final ByteBuffer byteBuffer) {
    }
  });

  private final long offset;
  private final RandomAccessDataFile owner;
  private final PoolPageKey myKey;

  private ByteBuffer buf;
  private boolean read = false;
  private boolean dirty = false;
  private int myFinalizationId;
  private BitSet myWriteMask;

  private static class PageLock {}
  private final PageLock lock = new PageLock();

  public Page(RandomAccessDataFile owner, long offset) {
    this.owner = owner;
    this.offset = offset;

    myKey = new PoolPageKey(owner, offset);
    read = false;
    dirty = false;
    myWriteMask = null;

    assert offset >= 0;
  }

  private void ensureRead() {
    if (!read) {
      if (myWriteMask != null) {
        byte[] content = new byte[PAGE_SIZE];
        final ByteBuffer b = getBuf();
        b.position(0);
        b.get(content, 0, PAGE_SIZE);

        owner.loadPage(this);
        for(int i=myWriteMask.nextSetBit(0); i>=0; i=myWriteMask.nextSetBit(i+1)) {
          b.put(i, content[i]);
        }
        myWriteMask = null;
      }
      else {
        owner.loadPage(this);
      }

      read = true;
    }
  }

  private void ensureReadOrWriteMaskExists() {
    dirty = true;
    if (read || myWriteMask != null) return;
    myWriteMask = new BitSet(PAGE_SIZE);
  }

  private static class Range {
    int start;
    int end;
  }
  private final Range myContinuousRange = new Range();

  @Nullable
  private Range calcContinousRange(final BitSet mask) {
    int lowestByte = mask.nextSetBit(0);
    int highestByte;
    if (lowestByte >= 0) {
      highestByte = mask.nextClearBit(lowestByte);
      if (highestByte > 0) {
        int nextChunk = mask.nextSetBit(highestByte);
        if (nextChunk < 0) {
          myContinuousRange.start = lowestByte;
          myContinuousRange.end = highestByte;
          return myContinuousRange;
        }
        else {
          return null;
        }
      }
      else {
        myContinuousRange.start = lowestByte;
        myContinuousRange.end = PAGE_SIZE;
        return myContinuousRange;
      }
    }
    else {
      return null;
    }

  }

  public void flush() {
    synchronized (lock) {
      if (dirty) {
        int start = 0;
        int end = PAGE_SIZE;
        if (myWriteMask != null) {
          Range range = calcContinousRange(myWriteMask);
          if (range == null) {
  //          System.out.println("Discountinous write of: " + myWriteMask.cardinality() + " bytes. Performing ensure read before flush.");
            ensureRead();
          }
          else {
            start = range.start;
            end = range.end;
          }
          myWriteMask = null;
        }

        if (end - start > 0) {
          owner.flushPage(this, start, end);
        }

        dirty = false;
      }
    }
  }

  public ByteBuffer getBuf() {
    synchronized (lock) {
      if (buf == null) {
        synchronized (ourBufferPool) {
          buf = ourBufferPool.alloc();
        }
      }
      return buf;
    }
  }

  private void recycle() {
    if (buf != null) {
      synchronized (ourBufferPool) {
        ourBufferPool.recycle(buf);
      }
    }

    buf = null;
    read = false;
    dirty = false;
    myWriteMask = null;
  }

  public long getOffset() {
    return offset;
  }

  public int put(long index, byte[] bytes, int off, int length) {
    synchronized (lock) {
      myFinalizationId = 0;
      ensureReadOrWriteMaskExists();

      final int start = (int)(index - offset);
      final ByteBuffer b = getBuf();
      b.position(start);

      int count = Math.min(length, PAGE_SIZE - start);
      b.put(bytes, off, count);

      if (myWriteMask != null) {
        myWriteMask.set(start, start + count);
      }
      return count;
    }
  }

  public int get(long index, byte[] bytes, int off, int length) {
    synchronized (lock) {
      myFinalizationId = 0;
      ensureRead();

      final int start = (int)(index - offset);
      final ByteBuffer b = getBuf();
      b.position(start);

      int count = Math.min(length, PAGE_SIZE - start);
      b.get(bytes, off, count);

      return count;
    }
  }

  @Nullable
  public FinalizationRequest prepareForFinalization(int finalizationId) {
    synchronized (lock) {
      if (dirty) {
        myFinalizationId = finalizationId;
        return new FinalizationRequest(this, finalizationId);
      }
      else {
        recycle();
        return null;
      }
    }
  }

  public RandomAccessDataFile getOwner() {
    return owner;
  }

  public PoolPageKey getKey() {
    return myKey;
  }

  public boolean flushIfFinalizationIdIsEqualTo(final long finalizationId) {
    synchronized (lock) {
      if (myFinalizationId == finalizationId) {
        flush();
        return true;
      }

      return false;
    }
  }

  public boolean recycleIfFinalizationIdIsEqualTo(final long finalizationId) {
    synchronized (lock) {
      if (myFinalizationId == finalizationId) {
        recycle();
        return true;
      }
      return false;
    }
  }

  @Override
  public String toString() {
    synchronized (lock) {
      return "Page[" + owner + ", dirty: " + dirty + ", offset=" + offset + "]";
    }
  }
}