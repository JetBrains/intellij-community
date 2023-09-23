// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class ResizeableMappedFile implements Forceable, Closeable {
  private static final Logger LOG = Logger.getInstance(ResizeableMappedFile.class);

  private volatile long myLogicalSize;
  private volatile long myLastWrittenLogicalSize;
  private final PagedFileStorage myStorage;
  private final int myInitialSize;

  static final int DEFAULT_ALLOCATION_ROUND_FACTOR = 4096;
  private int myRoundFactor = DEFAULT_ALLOCATION_ROUND_FACTOR;

  public ResizeableMappedFile(@NotNull Path file, int initialSize, @Nullable StorageLockContext lockContext, int pageSize,
                              boolean valuesAreBufferAligned) throws IOException {
    this(file, initialSize, lockContext, pageSize, valuesAreBufferAligned, false);
  }

  public ResizeableMappedFile(@NotNull Path file,
                              int initialSize,
                              @Nullable StorageLockContext lockContext,
                              int pageSize,
                              boolean valuesAreBufferAligned,
                              boolean nativeBytesOrder) throws IOException {
    myStorage = new PagedFileStorage(file, lockContext, pageSize, valuesAreBufferAligned, nativeBytesOrder);
    ensureParentDirectoryExists();
    myInitialSize = initialSize;
    myLastWrittenLogicalSize = myLogicalSize = readLength();
    if (myLastWrittenLogicalSize > 0 && !Files.exists(file)) {
      //probably, the main file was removed
      myLastWrittenLogicalSize = myLogicalSize = 0;
      writeLength(0);
    }
  }

  public boolean isNativeBytesOrder() {
    return myStorage.isNativeBytesOrder();
  }

  public void clear() throws IOException {
    myStorage.resize(0);
    myLogicalSize = 0;
    myLastWrittenLogicalSize = 0;
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
    }
    else {
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
    try (DataOutputStream stream = FileUtilRt.doIOOperation(lastAttempt -> {
      try {
        return new DataOutputStream(Files.newOutputStream(lengthFile));
      }
      catch (NoSuchFileException ex) {
        ensureParentDirectoryExists();
        if (!lastAttempt) return null;
        throw ex;
      }
    })) {
      if (stream != null) {
        stream.writeLong(len);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isDirty() {
    return myStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    ensureLengthWritten();
    myStorage.force();
  }

  private void ensureLengthWritten() {
    if (myLastWrittenLogicalSize != myLogicalSize) {
      writeLength(myLogicalSize);
      myLastWrittenLogicalSize = myLogicalSize;
    }
  }

  private void ensureParentDirectoryExists() throws IOException {
    Path parent = getLengthFile().getParent();
    if (!Files.exists(parent)) {
      Files.createDirectories(parent);
    }
  }

  private long readLength() throws IOException {
    final Path storageFile = myStorage.getFile();
    final Path lengthFile = getLengthFile();
    final long zero = 0L;
    if (!Files.exists(lengthFile) && (!Files.exists(storageFile) || Files.size(storageFile) == zero)) {
      writeLength(zero);
      return zero;
    }

    try (DataInputStream stream = new DataInputStream(Files.newInputStream(lengthFile, StandardOpenOption.READ))) {
      return stream.readLong();
    }
    catch (IOException e) {
      long realSize = realSize();
      writeLength(realSize);
      LOG.info("Can't find .len file for " + storageFile + ", re-creating it from actual file. " +
               "Storage size = " + realSize + ", file size = " + Files.size(storageFile), e);
      return realSize;
    }
  }

  public int getInt(long index) throws IOException {
    return myStorage.getInt(index);
  }

  public void putInt(long index, int value) throws IOException {
    ensureSize(index + 4);
    myStorage.putInt(index, value);
  }

  public long getLong(long index) throws IOException {
    return myStorage.getLong(index);
  }

  public void putLong(long index, long value) throws IOException {
    ensureSize(index + 8);
    myStorage.putLong(index, value);
  }

  public byte get(long index) throws IOException {
    return get(index, true);
  }

  public byte get(long index, boolean checkAccess) throws IOException {
    return myStorage.get(index, checkAccess);
  }

  public void get(long index, byte[] dst, int offset, int length, boolean checkAccess) throws IOException {
    myStorage.get(index, dst, offset, length, checkAccess);
  }

  public void put(long index, byte[] src, int offset, int length) throws IOException {
    ensureSize(index + length);
    myStorage.put(index, src, offset, length);
  }

  public void put(long index, @NotNull ByteBuffer buffer) throws IOException {
    ensureSize(index + (buffer.limit() - buffer.position()));
    myStorage.putBuffer(index, buffer);
  }

  public void setLogicalSize(long logicalSize) {
    myLogicalSize = logicalSize;
  }

  public long getLogicalSize() {
    return myLogicalSize;
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      new IOException("Failed to close ResizableMappedFile[" + getPagedFileStorage().getFile() + "]"),
      () -> {
        ensureLengthWritten();
        assert myLogicalSize == myLastWrittenLogicalSize;
        myStorage.force();
        boolean truncateOnClose = SystemProperties.getBooleanProperty("idea.resizeable.file.truncate.on.close", false);
        if (truncateOnClose && myLogicalSize < myStorage.length()) {
          myStorage.resize(myLogicalSize);
        }
      },
      myStorage::close
    );
  }

  public @NotNull PagedFileStorage getPagedFileStorage() {
    return myStorage;
  }

  public @NotNull StorageLockContext getStorageLockContext() {
    return myStorage.getStorageLockContext();
  }

  public <R> @NotNull R readInputStream(@NotNull ThrowableNotNullFunction<? super InputStream, R, ? extends IOException> consumer)
    throws IOException {
    return myStorage.readInputStream(consumer);
  }

  public <R> @NotNull R readChannel(@NotNull ThrowableNotNullFunction<? super ReadableByteChannel, R, ? extends IOException> consumer)
    throws IOException {
    return myStorage.readChannel(consumer);
  }

  public void lockRead() {
    myStorage.lockRead();
  }

  public void unlockRead() {
    myStorage.unlockRead();
  }

  public void lockWrite() {
    myStorage.lockWrite();
  }

  public void unlockWrite() {
    myStorage.unlockWrite();
  }

  /** Close the storage and remove all its data files */
  public void closeAndRemoveAllFiles() throws IOException {
    List<Exception> exceptions = new SmartList<>();

    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(myStorage::close));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(myStorage.getFile())));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(getLengthFile())));

    if (!exceptions.isEmpty()) {
      throw new IOException(new CompoundRuntimeException(exceptions));
    }
  }

  @Override
  public String toString() {
    return "ResizeableMappedFile[" + myStorage.toString() + "]";
  }
}
