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
import org.jetbrains.annotations.ApiStatus.Internal;
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

@Internal
public final class ResizeableMappedFile implements Forceable, Closeable {
  private static final Logger LOG = Logger.getInstance(ResizeableMappedFile.class);

  static final int DEFAULT_ALLOCATION_ROUND_FACTOR = 4096;

  private final PagedFileStorage storage;

  private final int initialSize;

  /**
   * Logical size == size of the data written in file.
   * File itself ({@code myStorage.getFile()}) could be bigger, since it is expanded in advance --
   * see {@link #expand(long)}/{@link #doRoundToFactor(long)} for details
   */
  private volatile long logicalSize;
  private volatile long lastWrittenLogicalSize;


  private int roundingFactor = DEFAULT_ALLOCATION_ROUND_FACTOR;

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
    this.initialSize = initialSize;
    storage = new PagedFileStorage(file, lockContext, pageSize, valuesAreBufferAligned, nativeBytesOrder);
    try {

      Path storageFile = storage.getFile();
      Path lengthFile = deriveLengthFile();

      //if parent directory !exist
      //   => both lengthFile & storageFile are !exist
      //   => writeLogicalSize() will call ensureParentDirectoryExists() on fail-path

      long storageFileSize = storage.length();
      if (!Files.exists(lengthFile) && storageFileSize == 0) {
        lastWrittenLogicalSize = logicalSize = 0;
        writeLogicalSize(0);
      }
      else {
        lastWrittenLogicalSize = logicalSize = readLogicalSize();
        if (lastWrittenLogicalSize > storageFileSize) {
          //main storage file was removed/truncated?
          LOG.warn("[" + storageFile.toAbsolutePath() + "] inconsistency: " +
                   "realFileSize(=" + storageFileSize + "b) > logicalSize(=" + lastWrittenLogicalSize + "b)" +
                   " -- storage file was removed/truncated? => resetting logical size to real size");
          lastWrittenLogicalSize = logicalSize = storageFileSize;
          writeLogicalSize(storageFileSize);
        }
      }
    }
    catch (Throwable t) {
      final Exception errorOnClose = ExceptionUtil.runAndCatch(
        storage::close
      );
      if (errorOnClose != null) {
        t.addSuppressed(errorOnClose);
      }
      throw t;
    }
  }

  public boolean isNativeBytesOrder() {
    return storage.isNativeBytesOrder();
  }

  public void clear() throws IOException {
    storage.resize(0);
    logicalSize = 0;
    lastWrittenLogicalSize = 0;
  }

  public long length() {
    return logicalSize;
  }

  private long realSize() {
    return storage.length();
  }

  void ensureSize(long pos) {
    logicalSize = Math.max(pos, logicalSize);
    expand(pos);
  }

  public void setRoundFactor(int roundingFactor) {
    this.roundingFactor = roundingFactor;
  }

  private void expand(long max) {
    long realSize = realSize();
    if (max <= realSize) return;
    long suggestedSize;

    if (realSize == 0) {
      suggestedSize = doRoundToFactor(Math.max(initialSize, max));
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
      storage.resize(suggestedSize);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private long doRoundToFactor(long suggestedSize) {
    int roundFactor = roundingFactor;
    if (suggestedSize % roundFactor != 0) {
      suggestedSize = (suggestedSize / roundFactor + 1) * roundFactor;
    }
    return suggestedSize;
  }

  private Path deriveLengthFile() {
    Path file = storage.getFile();
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
    return storage.isDirty();
  }

  @Override
  public void force() throws IOException {
    ensureLogicalSizeWritten();
    storage.force();
  }

  private void ensureLogicalSizeWritten() {
    if (lastWrittenLogicalSize != logicalSize) {
      writeLogicalSize(logicalSize);
      lastWrittenLogicalSize = logicalSize;
    }
  }

  private void ensureParentDirectoryExists() throws IOException {
    Path parentDir = storage.getFile().getParent();
    Files.createDirectories(parentDir);
  }

  /**
   * Reads 'logical' file size from .len file.
   * Logical size == size of the data written in file, while file itself could be bigger, since it is expanded in advance,
   * see {@link #expand(long)}
   * If .len file not exists, or empty -- re-creates it, and fill in the length of actual storageFile
   */
  private long readLogicalSize() throws IOException {
    Path storageFile = storage.getFile();
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
    return storage.getInt(index);
  }

  public void putInt(long index, int value) throws IOException {
    ensureSize(index + 4);
    storage.putInt(index, value);
  }

  public long getLong(long index) throws IOException {
    return storage.getLong(index);
  }

  public void putLong(long index, long value) throws IOException {
    ensureSize(index + 8);
    storage.putLong(index, value);
  }

  public byte get(long index) throws IOException {
    return get(index, true);
  }

  public byte get(long index, boolean checkAccess) throws IOException {
    return storage.get(index, checkAccess);
  }

  public void get(long index, byte[] dst, int offset, int length, boolean checkAccess) throws IOException {
    storage.get(index, dst, offset, length, checkAccess);
  }

  public void put(long index, byte[] src, int offset, int length) throws IOException {
    ensureSize(index + length);
    storage.put(index, src, offset, length);
  }

  public void put(long index, @NotNull ByteBuffer buffer) throws IOException {
    ensureSize(index + (buffer.limit() - buffer.position()));
    storage.putBuffer(index, buffer);
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Failed to close ResizableMappedFile[" + getPagedFileStorage().getFile() + "]"),
      () -> {
        ensureLogicalSizeWritten();
        assert logicalSize == lastWrittenLogicalSize;
        storage.force();
        boolean truncateOnClose = SystemProperties.getBooleanProperty("idea.resizeable.file.truncate.on.close", false);
        if (truncateOnClose && logicalSize < storage.length()) {
          storage.resize(logicalSize);
        }
      },
      storage::close
    );
  }

  public @NotNull PagedFileStorage getPagedFileStorage() {
    return storage;
  }

  public @NotNull StorageLockContext getStorageLockContext() {
    return storage.getStorageLockContext();
  }

  public <R> @NotNull R readInputStream(@NotNull ThrowableNotNullFunction<? super InputStream, R, ? extends IOException> consumer)
    throws IOException {
    return storage.readInputStream(consumer);
  }

  public <R> @NotNull R readChannel(@NotNull ThrowableNotNullFunction<? super ReadableByteChannel, R, ? extends IOException> consumer)
    throws IOException {
    return storage.readChannel(consumer);
  }

  public void lockRead() {
    storage.lockRead();
  }

  public void unlockRead() {
    storage.unlockRead();
  }

  public void lockWrite() {
    storage.lockWrite();
  }

  public void unlockWrite() {
    storage.unlockWrite();
  }

  //TODO RC: implement CleanableStorage instead

  /** Close the storage and remove all its data files */
  public void closeAndRemoveAllFiles() throws IOException {
    List<Exception> exceptions = new SmartList<>();

    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(storage::close));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(storage.getFile())));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> FileUtil.delete(deriveLengthFile())));

    if (!exceptions.isEmpty()) {
      throw new IOException(new CompoundRuntimeException(exceptions));
    }
  }

  @Override
  public String toString() {
    return "ResizeableMappedFile[" + storage.toString() + "]";
  }
}
