// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import java.util.List;

import static java.nio.file.StandardOpenOption.READ;

public final class ResizeableMappedFile implements Forceable, Closeable {
  private static final Logger LOG = Logger.getInstance(ResizeableMappedFile.class);

  static final int DEFAULT_ALLOCATION_ROUND_FACTOR = 4096;

  private final PagedFileStorage myStorage;

  private final int myInitialSize;

  /**
   * Logical size == size of the data written in file.
   * File itself ({@code myStorage.getFile()}) could be bigger, since it is expanded in advance --
   * see {@link #expand(long)}/{@link #doRoundToFactor(long)} for details
   */
  private volatile long myLogicalSize;
  private volatile long myLastWrittenLogicalSize;


  private int myRoundingFactor = DEFAULT_ALLOCATION_ROUND_FACTOR;

  public ResizeableMappedFile(@NotNull Path file,
                              int initialSize,
                              @Nullable StorageLockContext lockContext,
                              int pageSize,
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
    myInitialSize = initialSize;

    Path storageFile = myStorage.getFile();
    Path lengthFile = deriveLengthFile();

    //if parent directory !exist
    //   => both lengthFile & storageFile are !exist
    //   => writeLogicalSize() will call ensureParentDirectoryExists() on fail-path

    long storageFileSize = myStorage.length();
    if (!Files.exists(lengthFile) && storageFileSize == 0) {
      myLastWrittenLogicalSize = myLogicalSize = 0;
      writeLogicalSize(0);
    }
    else {
      myLastWrittenLogicalSize = myLogicalSize = readLogicalSize();
      if (myLastWrittenLogicalSize > storageFileSize) {
        //main storage file was removed/truncated?
        LOG.warn("[" + storageFile.toAbsolutePath() + "] inconsistency: " +
                 "realFileSize(=" + storageFileSize + "b) > logicalSize(=" + myLastWrittenLogicalSize + "b)" +
                 " -- storage file was removed/truncated? => resetting logical size to real size");
        myLastWrittenLogicalSize = myLogicalSize = storageFileSize;
        writeLogicalSize(storageFileSize);
      }
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

  void ensureSize(long pos) {
    myLogicalSize = Math.max(pos, myLogicalSize);
    expand(pos);
  }

  public void setRoundFactor(int roundingFactor) {
    myRoundingFactor = roundingFactor;
  }

  private void expand(long max) {
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
    int roundFactor = myRoundingFactor;
    if (suggestedSize % roundFactor != 0) {
      suggestedSize = (suggestedSize / roundFactor + 1) * roundFactor;
    }
    return suggestedSize;
  }

  private Path deriveLengthFile() {
    Path file = myStorage.getFile();
    return file.resolveSibling(file.getFileName() + ".len");
  }

  private void writeLogicalSize(long logicalSize) {
    Path lengthFile = deriveLengthFile();
    try {
      FileUtilRt.doIOOperation(lastAttempt -> {
        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(lengthFile))) {
          stream.writeLong(logicalSize);
          return Boolean.TRUE;//useless, but can't return null -- null is interpreted as 'need retry'
        }
        catch (IOException ex) {
          //noinspection InstanceofCatchParameter
          if (ex instanceof NoSuchFileException) {
            ensureParentDirectoryExists();
          }

          if (lastAttempt) {
            throw ex;
          }

          return null;
        }
      });
    }
    catch (IOException e) {
      LOG.error("Can't write logical size to [" + lengthFile.toAbsolutePath() + "]", e);
    }
  }

  @Override
  public boolean isDirty() {
    return myStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    ensureLogicalSizeWritten();
    myStorage.force();
  }

  private void ensureLogicalSizeWritten() {
    if (myLastWrittenLogicalSize != myLogicalSize) {
      writeLogicalSize(myLogicalSize);
      myLastWrittenLogicalSize = myLogicalSize;
    }
  }

  private void ensureParentDirectoryExists() throws IOException {
    Path parentDir = myStorage.getFile().getParent();
    Files.createDirectories(parentDir);
  }

  /**
   * Reads 'logical' file size from .len file.
   * Logical size == size of the data written in file, while file itself could be bigger, since it is expanded in advance,
   * see {@link #expand(long)}
   * If .len file not exists, or empty -- re-creates it, and fill in the length of actual storageFile
   */
  private long readLogicalSize() throws IOException {
    Path storageFile = myStorage.getFile();
    Path lengthFile = deriveLengthFile();

    try (DataInputStream stream = new DataInputStream(Files.newInputStream(lengthFile, READ))) {
      return stream.readLong();
    }
    catch (IOException e) {
      long realSize = realSize();
      writeLogicalSize(realSize);
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

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Failed to close ResizableMappedFile[" + getPagedFileStorage().getFile() + "]"),
      () -> {
        ensureLogicalSizeWritten();
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

  //TODO RC: implement CleanableStorage instead

  /** Close the storage and remove all its data files */
  public void closeAndRemoveAllFiles() throws IOException {
    List<Exception> exceptions = new SmartList<>();

    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(myStorage::close));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(myStorage.getFile())));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(deriveLengthFile())));

    if (!exceptions.isEmpty()) {
      throw new IOException(new CompoundRuntimeException(exceptions));
    }
  }

  @Override
  public String toString() {
    return "ResizeableMappedFile[" + myStorage.toString() + "]";
  }
}
