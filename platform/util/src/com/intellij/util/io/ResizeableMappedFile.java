/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ResizeableMappedFile implements Forceable {
  private static final Logger LOG = Logger.getInstance(ResizeableMappedFile.class);

  private static final boolean truncateOnClose = SystemProperties.getBooleanProperty("idea.resizeable.file.truncate.on.close", false);
  private long myLogicalSize;
  private long myLastWrittenLogicalSize;
  protected final PagedFileStorage myStorage;
  private final int myInitialSize;

  static final int DEFAULT_ALLOCATION_ROUND_FACTOR = 4096;
  private int myRoundFactor = DEFAULT_ALLOCATION_ROUND_FACTOR;

  public ResizeableMappedFile(@NotNull Path file, int initialSize, @Nullable StorageLockContext lockContext, int pageSize,
                              boolean valuesAreBufferAligned) {
    this(file, initialSize, lockContext, pageSize, valuesAreBufferAligned, false);
  }

  public ResizeableMappedFile(@NotNull Path file,
                              int initialSize,
                              @Nullable StorageLockContext lockContext,
                              int pageSize,
                              boolean valuesAreBufferAligned,
                              boolean nativeBytesOrder) {
    myStorage = new PagedFileStorage(file, lockContext, pageSize, valuesAreBufferAligned, nativeBytesOrder);
    myInitialSize = initialSize;
    myLastWrittenLogicalSize = myLogicalSize = readLength();
  }

  public long length() {
    return myLogicalSize;
  }

  private long realSize() {
    return myStorage.length();
  }

  void ensureSize(final long pos) {
    myLogicalSize = Math.max(pos, myLogicalSize);
    expand(pos);
  }

  public void setRoundFactor(int roundFactor) {
    myRoundFactor = roundFactor;
  }

  private void expand(final long max) {
    long realSize = realSize();
    if (max <= realSize) return;
    long suggestedSize;

    if (realSize == 0) {
      suggestedSize = doRoundToFactor(Math.max(myInitialSize, max));
    } else {
      suggestedSize = Math.max(realSize + 1, 2); // suggestedSize should increase with int multiplication on 1.625 factor

      while (max > suggestedSize) {
        long newSuggestedSize = suggestedSize * 13 >> 3;
        if (newSuggestedSize >= Integer.MAX_VALUE) {
          suggestedSize += suggestedSize / 5;
        }
        else {
          suggestedSize = newSuggestedSize;
        }
      }

      suggestedSize = doRoundToFactor(suggestedSize);
    }

    try {
      myStorage.resize(suggestedSize);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private long doRoundToFactor(long suggestedSize) {
    int roundFactor = myRoundFactor;
    if (suggestedSize % roundFactor != 0) {
      suggestedSize = (suggestedSize / roundFactor + 1) * roundFactor;
    }
    return suggestedSize;
  }

  private Path getLengthFile() {
    Path file = myStorage.getFile();
    return file.resolveSibling(file.getFileName() + ".len");
  }

  private void writeLength(final long len) {
    final Path lengthFile = getLengthFile();
    DataOutputStream stream = null;
    try {
      stream = FileUtilRt.doIOOperation(lastAttempt -> {
        try {
          return new DataOutputStream(Files.newOutputStream(lengthFile));
        }
        catch (NoSuchFileException ex) {
          Path parent = lengthFile.getParent();
          if (!Files.exists(parent)) {
            Files.createDirectories(parent);
          }
          if (!lastAttempt) return null;
          throw ex;
        }
      });
      if (stream != null) stream.writeLong(len);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public boolean isDirty() {
    return myStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    if (isDirty()) {
      if (myLastWrittenLogicalSize != myLogicalSize) {
        writeLength(myLogicalSize);
        myLastWrittenLogicalSize = myLogicalSize;
      }
    }
    myStorage.force();
  }

  private long readLength() {
    Path lengthFile = getLengthFile();
    if (!Files.exists(lengthFile)) return 0;

    try (DataInputStream stream = new DataInputStream(Files.newInputStream(lengthFile, StandardOpenOption.READ))) {
      return stream.readLong();
    }
    catch (IOException e) {
      long realSize = realSize();
      writeLength(realSize);
      LOG.error(e);
      return realSize;
    }
  }

  public int getInt(long index) {
    return myStorage.getInt(index);
  }

  public void putInt(long index, int value) {
    ensureSize(index + 4);
    myStorage.putInt(index, value);
  }

  public long getLong(long index) {
    return myStorage.getLong(index);
  }

  public void putLong(long index, long value) {
    ensureSize(index + 8);
    myStorage.putLong(index, value);
  }

  public byte get(long index) {
    return myStorage.get(index);
  }

  public void get(long index, byte[] dst, int offset, int length) {
    myStorage.get(index, dst, offset, length);
  }

  public void put(long index, byte[] src, int offset, int length) {
    ensureSize(index + length);
    myStorage.put(index, src, offset, length);
  }

  public void close() throws IOException {
    List<Exception> exceptions = new SmartList<>();
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> {
      force();
      if (truncateOnClose && myLogicalSize < myStorage.length()) {
        myStorage.resize(myLogicalSize);
      }
    }));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> myStorage.close()));
    if (!exceptions.isEmpty()) {
      throw new IOException(new CompoundRuntimeException(exceptions));
    }
  }

  @NotNull
  public PagedFileStorage getPagedFileStorage() {
    return myStorage;
  }
}
