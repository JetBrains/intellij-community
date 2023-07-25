// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage.Page;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

import static com.intellij.util.io.IOUtil.MiB;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Helper for creating {@link VirtualFileWithId}-associated storages based on memory-mapped file.
 * <p/>
 * This is 'helper', not full-fledged implementation, nor a good abstraction/encapsulation -- i.e.
 * one still needs to understand the underlying code.
 */
@ApiStatus.Internal
public class MappedFileStorageHelper implements AutoCloseable {

  //MAYBE:
  //    1) Versioning: better have 2 versions: INTERNAL_VERSION (i.e. header format version, managed by the class
  //       itself) and ATTRIBUTE_VERSION (arbitrary version tag managed bt client to track their way of use the attribute)
  //       It is OK to use 2 bytes for each version, and combine them both to a 4 byte header field.
  //    2) Default value: mapped file has 0 as default value. We could stay with it (i.e. define 0 as default value
  //       on API level), or allow to set defaultValue in ctor.
  //       To implement second option we need to store defaultValue in the header, and apply (0 <=> defaultValue)
  //       replacement on each read&write op. Now, it could be an overkill -- maybe default=0 is OK for the most
  //       clients, and no need for complications like that.
  //    3) allocatedRecordsCount: seems like here we don't need that number, because valid fileId range is limited
  //       by VFS -- anything in [1..VFS.maxAllocatedID] is valid, regardless of was apt slot allocated already, or
  //       not.
  //    4) connectionStatus: seems like we don't need it either, because updates are atomic, hence every saved state
  //       is at least self-consistent -- particular file's attribute was either updated or not, but not partially
  //       updated

  public static final class HeaderLayout {
    public static final int VERSION_OFFSET = 0;
    /** Next field is int64, must be 64-aligned for volatile access, so insert additional int32 */
    public static final int RESERVED_OFFSET = VERSION_OFFSET + Integer.BYTES;

    public static final int VFS_CREATION_TIMESTAMP_OFFSET = RESERVED_OFFSET + Integer.BYTES;

    public static final int FIRST_FREE_FIELD = VFS_CREATION_TIMESTAMP_OFFSET + Long.BYTES;

    //reserve [8 x int64] just for the case
    public static final int HEADER_SIZE = 8 * Long.BYTES;
  }


  public static final int DEFAULT_PAGE_SIZE = 4 * MiB;

  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder())
    .withInvokeExactBehavior();
  private static final VarHandle LONG_HANDLE = byteBufferViewVarHandle(long[].class, nativeOrder())
    .withInvokeExactBehavior();


  private final int bytesPerRow;

  private final @NotNull MMappedFileStorage storage;

  private final @NotNull IntSupplier maxAllocatedFileIdSupplier;

  public MappedFileStorageHelper(@NotNull MMappedFileStorage storage,
                                 int bytesPerRow,
                                 @NotNull IntSupplier maxRowsSupplier) throws IOException {
    if (bytesPerRow % Integer.BYTES != 0) {
      //nothing really prevents us from support non-32b-aligned rows, but current field accessors are
      // only long/int anyway, and also .clear() method will need adjustment for non-32b-aligned rows:
      throw new IllegalArgumentException(
        "bytesPerRow(=" + bytesPerRow + ") is not int32-aligned: so far only 32b-aligned rows are supported");
    }
    if (storage.pageSize() % bytesPerRow != 0) {
      throw new IllegalArgumentException(
        "bytesPerRow(=" + bytesPerRow + ") is not aligned with pageSize(=" + storage.pageSize() + "): rows must be page-aligned");
    }

    this.storage = storage;
    this.bytesPerRow = bytesPerRow;
    maxAllocatedFileIdSupplier = maxRowsSupplier;
  }

  public int getVersion() throws IOException {
    return readIntHeaderField(HeaderLayout.VERSION_OFFSET);
  }

  public void setVersion(int version) throws IOException {
    writeIntHeaderField(HeaderLayout.VERSION_OFFSET, version);
  }

  public long getVFSCreationTag() throws IOException {
    return readLongHeaderField(HeaderLayout.VFS_CREATION_TIMESTAMP_OFFSET);
  }

  public void setVFSCreationTag(long vfsCreationTag) throws IOException {
    writeLongHeaderField(HeaderLayout.VFS_CREATION_TIMESTAMP_OFFSET, vfsCreationTag);
  }


  public int readIntField(@NotNull VirtualFile vFile,
                          int fieldOffset) throws IOException {
    int fileId = fileId(vFile);
    return readIntField(fileId, fieldOffset);
  }

  public void writeIntField(@NotNull VirtualFile vFile,
                            int fieldOffset,
                            int value) throws IOException {
    int fileId = fileId(vFile);
    writeIntField(fileId, fieldOffset, value);
  }

  public int updateIntField(@NotNull VirtualFile vFile,
                            int fieldOffset,
                            @NotNull IntUnaryOperator updateOperator) throws IOException {
    int fileId = fileId(vFile);
    return updateIntField(fileId, fieldOffset, updateOperator);
  }


  public long readLongField(@NotNull VirtualFile vFile,
                            int fieldOffset) throws IOException {
    int fileId = fileId(vFile);
    return readIntField(fileId, fieldOffset);
  }

  public void writeLongField(@NotNull VirtualFile vFile,
                             int fieldOffset,
                             long value) throws IOException {
    int fileId = fileId(vFile);
    writeLongField(fileId, fieldOffset, value);
  }

  public long updateLongField(@NotNull VirtualFile vFile,
                              int fieldOffset,
                              @NotNull LongUnaryOperator updateOperator) throws IOException {
    int fileId = fileId(vFile);
    return updateLongField(fileId, fieldOffset, updateOperator);
  }

  public void clear() throws IOException {
    //MAYBE RC: it could be made much faster
    int maxAllocatedFileId = maxAllocatedFileID();
    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedFileId; fileId++) {
      for (int fieldOffset = 0; fieldOffset < bytesPerRow; fieldOffset += Integer.BYTES) {
        writeIntField(fileId, fieldOffset, 0);
      }
    }
    //clear headers also? or leave it to client?
    setVersion(0);
    setVFSCreationTag(0);
  }

  public void fsync() throws IOException {
    storage.fsync();
  }

  @Override
  public void close() throws IOException {
    //MAYBE RC: is it better to always fsync here? -- or better state that fsync should be called explicitly, if
    //          needed, otherwise leave it to OS to decide when to flush the pages?
    storage.close();
  }

  /**
   * Closes the file, releases the mapped buffers, and tries to delete the file.
   * <p/>
   * Implementation note: the exact moment file memory-mapping is actually released and the file could be
   * deleted -- is very OS/platform-dependent. E.g., Win is known to keep file 'in use' for some time even
   * after unmap() call is already finished. JVM release mapped buffers with GC, which adds another level
   * of uncertainty. Hence, if one needs to re-create the storage, it may be more reliable to just .clear()
   * the current storage, than to close->remove->create-fresh-new.
   */
  public void closeAndRemove() throws IOException {
    close();
    FileUtil.delete(storage.storagePath());
  }

  @Override
  public String toString() {
    return "MappedFileStorageHelper[" + storage.storagePath() + "]";
  }

  public int readIntField(int fileId,
                          int fieldOffsetInRow) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    return (int)INT_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  }

  public void writeIntField(int fileId,
                            int fieldOffsetInRow,
                            int attributeValue) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    INT_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
  }

  /* ============== implementation: ====================================================================== */

  @VisibleForTesting
  int updateIntField(int fileId,
                     int fieldOffsetInRow,
                     @NotNull IntUnaryOperator updateOperator) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();
    while (true) {//CAS loop:
      int currentValue = (int)INT_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
      int newValue = updateOperator.applyAsInt(currentValue);
      if (INT_HANDLE.compareAndSet(rawPageBuffer, offsetInPage, currentValue, newValue)) {
        return currentValue;
      }
    }
  }

  @VisibleForTesting
  long readLongField(int fileId,
                     int fieldOffsetInRow) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    return (long)LONG_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  }

  @VisibleForTesting
  void writeLongField(int fileId,
                      int fieldOffsetInRow,
                      long attributeValue) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    LONG_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
  }

  @VisibleForTesting
  long updateLongField(int fileId,
                       int fieldOffsetInRow,
                       @NotNull LongUnaryOperator updateOperator) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();
    while (true) {//CAS loop:
      long currentValue = (long)LONG_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
      long newValue = updateOperator.applyAsLong(currentValue);
      if (LONG_HANDLE.compareAndSet(rawPageBuffer, offsetInPage, currentValue, newValue)) {
        return currentValue;
      }
    }
  }

  //MAYBE RC: add accessors for shortField, byteField?

  public int readIntHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (int)INT_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  public long readLongHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (long)LONG_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  public void writeIntHeaderField(int headerRelativeOffsetBytes,
                                  int headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      INT_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }

  public void writeLongHeaderField(int headerRelativeOffsetBytes,
                                   long headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      LONG_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }

  private int maxAllocatedFileID() {
    return maxAllocatedFileIdSupplier.getAsInt();
  }

  private void checkFileIdValid(int fileId) {
    int maxAllocatedID = maxAllocatedFileID();
    if (fileId < FSRecords.ROOT_FILE_ID || fileId > maxAllocatedID) {
      throw new IllegalArgumentException(
        "fileId[#" + fileId + "] is outside of allocated range [" + FSRecords.ROOT_FILE_ID + ".." + maxAllocatedID + "]");
    }
  }

  private int toOffsetInFile(int fileId) {
    return (fileId - FSRecords.ROOT_FILE_ID) * bytesPerRow + HeaderLayout.HEADER_SIZE;
  }

  private int fileId(@NotNull VirtualFile vFile) {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be VirtualFileWithId");
    }
    VirtualFileWithId fileWithId = (VirtualFileWithId)vFile;
    int fileId = fileWithId.getId();
    checkFileIdValid(fileId);
    return fileId;
  }
}
