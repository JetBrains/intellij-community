// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.mapped;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.Unmappable;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.Page;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

import static com.intellij.util.io.IOUtil.MiB;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Helper for creating {@link VirtualFileWithId}-associated storages based on memory-mapped file.
 * <p/>
 * Basic idea is: storage is a fixed-size header and a set of fixed-size records, one record per each
 * {@link VirtualFileWithId} known to VFS.
 * Header is up to 64 bytes, first 4 bytes is a version, next 8 is a VFS tag (creation timestamp),
 * the remaining 52 bytes could be used as needed (see {@link #writeIntHeaderField(int, int)}, {@link #writeLongHeaderField(int, long)}).
 * Record (row) is an arbitrary but fixed size, given in ctor (bytesPerRow).
 * <p/>
 * Keep in mind that CPUs universally support atomic/volatile access to N-bytes word only for N-aligned
 * offsets. Which means: if you want int64 field in your record, you need to compose record in such a way
 * this field's offset is always 8-byte-aligned. Which means bytesPerRow must be a factor of 8 and a
 * particular field offset in the record must also be a factor of 8.
 * <p/>
 * This is 'helper', not full-fledged implementation, nor a good abstraction/encapsulation -- i.e.
 * one still needs to understand the underlying code.
 */
@ApiStatus.Internal
public final class MappedFileStorageHelper implements Closeable, CleanableStorage, Unmappable {
  /**
   * Keeps a registry of all {@link MappedFileStorageHelper} -- prevents creating duplicates, i.e. >1 storage
   * for the same path.
   */
  //@GuardedBy(storagesRegistry)
  private static final Map<Path, MappedFileStorageHelper> storagesRegistry = new HashMap<>();

  public static @NotNull MappedFileStorageHelper openHelper(@NotNull FSRecordsImpl vfs,
                                                            @NotNull Path absoluteStoragePath,
                                                            int bytesPerRow,
                                                            boolean checkFileIdsBelowMax) throws IOException {
    if (!absoluteStoragePath.isAbsolute()) {
      throw new IllegalArgumentException("absoluteStoragePath(=" + absoluteStoragePath + ") is not absolute");
    }
    if (bytesPerRow <= 0) {
      throw new IllegalArgumentException("bytesPerRow(=" + bytesPerRow + ") must be >0");
    }
    var storageDir = absoluteStoragePath.getParent().normalize();

    Files.createDirectories(storageDir);
    PersistentFSConnection connection = vfs.connection();

    var recordsStorage = connection.getRecords();

    synchronized (storagesRegistry) {
      MappedFileStorageHelper alreadyExistingHelper = storagesRegistry.get(absoluteStoragePath);
      if (alreadyExistingHelper != null && alreadyExistingHelper.storage.isOpen()) {
        if (alreadyExistingHelper.bytesPerRow != bytesPerRow) {
          throw new IllegalStateException(
            "StorageHelper[" + absoluteStoragePath + "] is already registered, " +
            "but with .bytesPerRow(=" + bytesPerRow + ") != storage.bytesPerRow(=" + alreadyExistingHelper.bytesPerRow + ")"
          );
        }
        return alreadyExistingHelper;
      }

      return MMappedFileStorageFactory.withDefaults()
        .pageSize(DEFAULT_PAGE_SIZE)
        .wrapStorageSafely(
          absoluteStoragePath,
          mappedFileStorage -> {
            MappedFileStorageHelper storageHelper = new MappedFileStorageHelper(
              mappedFileStorage,
              bytesPerRow,
              recordsStorage::maxAllocatedID,
              checkFileIdsBelowMax
            );
            storagesRegistry.put(absoluteStoragePath, storageHelper);
            return storageHelper;
          }
        );
    }
  }

  public static @NotNull MappedFileStorageHelper openHelper(@NotNull FSRecordsImpl vfs,
                                                            @NotNull String storageName,
                                                            int bytesPerRow) throws IOException {
    return openHelper(vfs, storageName, bytesPerRow, true);
  }

  public static @NotNull MappedFileStorageHelper openHelper(@NotNull FSRecordsImpl vfs,
                                                            @NotNull String storageName,
                                                            int bytesPerRow,
                                                            boolean checkFileIdsBelowMax) throws IOException {
    PersistentFSConnection connection = vfs.connection();
    Path fastAttributesDir = connection.getPersistentFSPaths().storagesSubDir("extended-attributes");
    Path storagePath = fastAttributesDir.resolve(storageName).toAbsolutePath();
    return openHelper(vfs, storagePath, bytesPerRow, checkFileIdsBelowMax);
  }

  public static @NotNull MappedFileStorageHelper openHelperAndVerifyVersions(@NotNull FSRecordsImpl vfs,
                                                                             @NotNull String storageName,
                                                                             int storageFormatVersion,
                                                                             int bytesPerRow) throws IOException {
    return openHelperAndVerifyVersions(vfs, storageName, storageFormatVersion, bytesPerRow, true);
  }

  public static @NotNull MappedFileStorageHelper openHelperAndVerifyVersions(@NotNull FSRecordsImpl vfs,
                                                                             @NotNull String storageName,
                                                                             int storageFormatVersion,
                                                                             int bytesPerRow,
                                                                             boolean checkFileIdBelowMax) throws IOException {
    MappedFileStorageHelper helper = openHelper(vfs, storageName, bytesPerRow, checkFileIdBelowMax);
    verifyTagsAndVersions(helper, vfs.getCreationTimestamp(), storageFormatVersion);
    return helper;
  }

  public static @NotNull MappedFileStorageHelper openHelperAndVerifyVersions(@NotNull FSRecordsImpl vfs,
                                                                             @NotNull Path absoluteStoragePath,
                                                                             int storageFormatVersion,
                                                                             int bytesPerRow,
                                                                             boolean checkFileIdBelowMax) throws IOException {
    MappedFileStorageHelper helper = openHelper(vfs, absoluteStoragePath, bytesPerRow, checkFileIdBelowMax);
    verifyTagsAndVersions(helper, vfs.getCreationTimestamp(), storageFormatVersion);
    return helper;
  }

  public static void verifyTagsAndVersions(@NotNull MappedFileStorageHelper helper,
                                           long vfsCreationTag,
                                           int storageFormatVersion) throws IOException {
    if (helper.getVFSCreationTag() != vfsCreationTag) {
      helper.clear();
    }
    if (helper.getVersion() != storageFormatVersion) {
      helper.clear();
    }
    helper.setVFSCreationTag(vfsCreationTag);
    helper.setVersion(storageFormatVersion);
  }

  @VisibleForTesting
  public static Map<Path, MappedFileStorageHelper> registeredStorages() {
    return storagesRegistry;
  }

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

  //TODO RC: byteBufferViewVarHandle(byte[].class) is not supported by JDK
  //private static final VarHandle BYTE_HANDLE = byteBufferViewVarHandle(byte[].class, nativeOrder())
  //  .withInvokeExactBehavior();
  private static final VarHandle SHORT_HANDLE = byteBufferViewVarHandle(short[].class, nativeOrder())
    .withInvokeExactBehavior();
  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder())
    .withInvokeExactBehavior();
  private static final VarHandle LONG_HANDLE = byteBufferViewVarHandle(long[].class, nativeOrder())
    .withInvokeExactBehavior();


  private final int bytesPerRow;
  private final transient byte[] rowOfZeroes;

  private final @NotNull MMappedFileStorage storage;

  private final @NotNull IntSupplier maxAllocatedFileIdSupplier;

  /**
   * If true, fileId arg of every method is checked to be in (0..maxId].
   * If false, fileId arg is checked only to be >0 (negative fileId ruins addressing schema).
   * FIXME RC: it MUST be set true all the time -- it should _never_ be _any_ fileId in use outside (0..maxAllocatedId].
   *           False is a temporary backward compatibility option: it seems like in some use-cases somehow
   *           the constraint is violated, but we have no time/hands to to find out why.
   */
  private final boolean checkFileIdsBelowMax;

  private MappedFileStorageHelper(@NotNull MMappedFileStorage storage,
                                  int bytesPerRow,
                                  @NotNull IntSupplier maxRowsSupplier,
                                  boolean checkFileIdsBelowMax) throws IOException {
    if (storage.pageSize() % bytesPerRow != 0) {
      throw new IllegalArgumentException(
        "bytesPerRow(=" + bytesPerRow + ") is not aligned with pageSize(=" + storage.pageSize() + "): rows must be page-aligned");
    }

    this.storage = storage;
    this.bytesPerRow = bytesPerRow;
    maxAllocatedFileIdSupplier = maxRowsSupplier;
    this.checkFileIdsBelowMax = checkFileIdsBelowMax;

    this.rowOfZeroes = new byte[bytesPerRow];
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

  public int bytesPerRow() {
    return bytesPerRow;
  }


  public int readIntField(@NotNull VirtualFile vFile,
                          int fieldOffset) throws IOException {
    int fileId = extractFileId(vFile);
    return readIntField(fileId, fieldOffset);
  }

  public void writeIntField(@NotNull VirtualFile vFile,
                            int fieldOffset,
                            int value) throws IOException {
    int fileId = extractFileId(vFile);
    writeIntField(fileId, fieldOffset, value);
  }

  public int updateIntField(@NotNull VirtualFile vFile,
                            int fieldOffset,
                            @NotNull IntUnaryOperator updateOperator) throws IOException {
    int fileId = extractFileId(vFile);
    return updateIntField(fileId, fieldOffset, updateOperator);
  }


  public long readLongField(@NotNull VirtualFile vFile,
                            int fieldOffset) throws IOException {
    int fileId = extractFileId(vFile);
    return readLongField(fileId, fieldOffset);
  }

  public void writeLongField(@NotNull VirtualFile vFile,
                             int fieldOffset,
                             long value) throws IOException {
    int fileId = extractFileId(vFile);
    writeLongField(fileId, fieldOffset, value);
  }

  public long updateLongField(@NotNull VirtualFile vFile,
                              int fieldOffset,
                              @NotNull LongUnaryOperator updateOperator) throws IOException {
    int fileId = extractFileId(vFile);
    return updateLongField(fileId, fieldOffset, updateOperator);
  }

  /**
   * Clears all storage content -- headers and data -- as-if storage was just created from 0.
   * It means all the fields have their default values (=0)
   * BEWARE: memory semantics of clear implementation is currently 'plain write', so it doesn't work
   * reliable with 'volatile' semantics of other fields accessors
   */
  public void clear() throws IOException {
    clearImpl(/*clearHeaders: */ true);
  }

  /**
   * Clears all storage records, but don't touch headers.
   * All records fields will have their default values (=0)
   * BEWARE: memory semantics of clear implementation is currently 'plain write', so it doesn't work
   * reliable with 'volatile' semantics of other fields accessors
   */
  public void clearRecords() throws IOException {
    clearImpl(/*clearHeaders: */ false);
  }

  private void clearImpl(boolean clearHeaders) throws IOException {
    int startOffsetInFile = clearHeaders ? 0 : HeaderLayout.HEADER_SIZE;
    //IDEA-330224: It is important to zeroize until the EOF, not until the current maxAllocatedID
    // rows, because storage could be created and filled with previous VFS there maxAllocatedID=1e6,
    // but now VFS rebuilds itself, and maxAllocatedID=1e3 -- and there are rows [1e3..1e6] filled
    // with outdated data, which won't be cleared
    storage.zeroizeTillEOF(startOffsetInFile);

    //TODO RC: which memory semantics .clear() should have? Now it is just 'plain write', while
    //         all other access methods uses 'volatile read/write'. For current use-case
    //         (initialization) it is OK, since values are safe-published anyway, but for other
    //         use-cases it could lead to troubles.
    //         On the other hand: semantics of .clear() in a concurrent context is anyway tricky:
    //         we can't clear all content atomically.
    //         So the best option I see is: prohibit .clear() use in concurrent context at all
    //         -- state it has undefined behavior in such a context.
  }

  /** It should be used in a rare occasions only: see {@link MMappedFileStorage#FSYNC_ON_FLUSH_BY_DEFAULT} docs */
  public void fsync() throws IOException {
    storage.fsync();
  }

  @Override
  public void close() throws IOException {
    //We don't fsync() by default on close -- leave it to OS to decide when to flush the pages
    storage.close();

    storagesRegistry.remove(storage.storagePath());
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    storage.closeAndUnsafelyUnmap();

    storagesRegistry.remove(storage.storagePath());
  }

  /** Closes the file, releases the mapped buffers, and 'make the best effort' to delete the file. */
  @Override
  public void closeAndClean() throws IOException {
    closeAndUnsafelyUnmap();
    storage.closeAndClean();
  }

  @Override
  public String toString() {
    return "MappedFileStorageHelper[" + storage.storagePath() + "]";
  }

  //TODO RC: We can fill the row just byte-by-bytes, but we can't keep reasonable atomicity/memory semantics
  //         -- to write 4 bytes is not the same as write 1 int. I doubt: is it really worth to deal with all
  //         possible complications arising from that, or better to keep that pandora-box closed, and not
  //         expose clearRow() method? If users want to clear the row -- they could implement it with
  //         .writeXXXField() methods.
  private void clearRow(int fileId) throws IOException {
    int offsetInFile = toOffsetInFile(fileId);
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    rawPageBuffer.put(offsetInPage, rowOfZeroes);//plain write, not volatile!
  }

  //public byte readByteField(int fileId,
  //                          int fieldOffsetInRow) throws IOException {
  //  int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
  //  int offsetInPage = storage.toOffsetInPage(offsetInFile);
  //
  //  Page page = storage.pageByOffset(offsetInFile);
  //  ByteBuffer rawPageBuffer = page.rawPageBuffer();
  //
  //  return (byte)BYTE_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  //}
  //
  //public void writeByteField(int fileId,
  //                           int fieldOffsetInRow,
  //                           byte attributeValue) throws IOException {
  //  int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
  //  int offsetInPage = storage.toOffsetInPage(offsetInFile);
  //
  //  Page page = storage.pageByOffset(offsetInFile);
  //  ByteBuffer rawPageBuffer = page.rawPageBuffer();
  //
  //  BYTE_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
  //}


  public short readShortField(int fileId,
                              int fieldOffsetInRow) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    return (short)SHORT_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  }

  public void writeShortField(int fileId,
                              int fieldOffsetInRow,
                              short attributeValue) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    SHORT_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
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

  public int updateIntField(int fileId,
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


  public long readLongField(int fileId,
                            int fieldOffsetInRow) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    return (long)LONG_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  }

  public void writeLongField(int fileId,
                             int fieldOffsetInRow,
                             long attributeValue) throws IOException {
    int offsetInFile = toOffsetInFile(fileId) + fieldOffsetInRow;
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    LONG_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
  }

  public long updateLongField(int fileId,
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

  //MAYBE RC: add accessors for headerShortField, headerByteField?

  public int readIntHeaderField(int headerRelativeOffset) throws IOException {
    checkHeaderFieldOffset(headerRelativeOffset);
    Page page = storage.pageByOffset(headerRelativeOffset);
    return (int)INT_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffset);
  }

  public long readLongHeaderField(int headerRelativeOffset) throws IOException {
    checkHeaderFieldOffset(headerRelativeOffset);
    Page page = storage.pageByOffset(headerRelativeOffset);
    return (long)LONG_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffset);
  }

  public void writeIntHeaderField(int headerRelativeOffset,
                                  int headerFieldValue) throws IOException {
    checkHeaderFieldOffset(headerRelativeOffset);
    Page page = storage.pageByOffset(headerRelativeOffset);
    INT_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffset, headerFieldValue);
  }

  public void writeLongHeaderField(int headerRelativeOffset,
                                   long headerFieldValue) throws IOException {
    checkHeaderFieldOffset(headerRelativeOffset);
    Page page = storage.pageByOffset(headerRelativeOffset);
    LONG_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffset, headerFieldValue);
  }


  // ============== implementation: ======================================================================

  private int toOffsetInFile(int fileId) {
    checkFileIdValid(fileId);
    int offsetInFile = (fileId - FSRecords.ROOT_FILE_ID) * bytesPerRow + HeaderLayout.HEADER_SIZE;
    if (offsetInFile < 0) {
      throw new AssertionError("fileId(=" + fileId + ") x bytesPerRow(=" + bytesPerRow + ") is too big: " +
                               "offsetInFile(=" + offsetInFile + ") must be positive");
    }
    return offsetInFile;
  }

  private void checkFileIdValid(int fileId) {
    if (checkFileIdsBelowMax) {
      int maxAllocatedID = maxAllocatedFileID();
      if (fileId < FSRecords.ROOT_FILE_ID || fileId > maxAllocatedID) {
        throw new IllegalArgumentException(
          "fileId[#" + fileId + "] is outside of allocated range [" + FSRecords.ROOT_FILE_ID + ".." + maxAllocatedID + "]");
      }
    }
    else {
      if (fileId < FSRecords.ROOT_FILE_ID) {
        throw new IllegalArgumentException(
          "fileId[#" + fileId + "] is invalid, must be >=" + FSRecords.ROOT_FILE_ID);
      }
    }
  }

  private int maxAllocatedFileID() {
    return maxAllocatedFileIdSupplier.getAsInt();
  }

  private static void checkHeaderFieldOffset(int headerRelativeOffset) {
    if (headerRelativeOffset >= HeaderLayout.HEADER_SIZE) {
      throw new IllegalArgumentException(
        "Header offset(=" + headerRelativeOffset + ") is outside of header[0.." + HeaderLayout.HEADER_SIZE + ")");
    }
  }

  private static int extractFileId(@NotNull VirtualFile vFile) {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be VirtualFileWithId");
    }
    VirtualFileWithId fileWithId = (VirtualFileWithId)vFile;

    return fileWithId.getId();
  }
}
