// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage.Page;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import static com.intellij.util.io.IOUtil.MiB;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Storage for {@link VirtualFileWithId}-associated attributes of type int.
 * <p/>
 * Experimental approach to making small fixed-size attributes very fast.
 * Semantically it is an analog of regular VFS attribute (see {@link PersistentFS#readAttribute(VirtualFile, FileAttribute)},
 * {@link PersistentFS#writeAttribute(VirtualFile, FileAttribute)}), but only
 * single int32 could be stored per file.
 * <p/>
 * In return, it is 10-20x faster single-threaded, and up to 100x multithreaded
 * than store the same int32 in regular file attribute.
 * <p/>
 * There are other caveats, pitfalls and dragons, so beware
 */
@ApiStatus.Internal
public class IntFileAttributesStorage implements AutoCloseable {

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

  private static final int HEADER_VERSION_OFFSET = 0;
  /** Next field is int64, must be 64-aligned for volatile access, so insert additional int32 */
  private static final int HEADER_RESERVED_OFFSET = HEADER_VERSION_OFFSET + Integer.BYTES;

  private static final int HEADER_VFS_CREATION_TIMESTAMP_OFFSET = HEADER_RESERVED_OFFSET + Integer.BYTES;

  //reserve [8 x int64] just for the case
  private static final int HEADER_SIZE = 8 * Long.BYTES;

  private static final int DEFAULT_PAGE_SIZE = 4 * MiB;

  private static final int BYTES_PER_ATTRIBUTE = Integer.BYTES;

  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder())
    .withInvokeExactBehavior();
  private static final VarHandle LONG_HANDLE = byteBufferViewVarHandle(long[].class, nativeOrder())
    .withInvokeExactBehavior();


  private final @NotNull MMappedFileStorage storage;

  private final @NotNull IntSupplier maxAllocatedFileIdSupplier;


  public static @NotNull IntFileAttributesStorage openOrCreate(@NotNull FSRecordsImpl vfs,
                                                               @NotNull String name) throws IOException {
    Path fastAttributesDir = Paths.get(FSRecords.getCachesDir(), "extended-attributes");
    Files.createDirectories(fastAttributesDir);

    Path storagePath = fastAttributesDir.resolve(name);
    long maxFileSize = HEADER_SIZE + BYTES_PER_ATTRIBUTE * (long)Integer.MAX_VALUE;
    MMappedFileStorage storage = new MMappedFileStorage(storagePath, DEFAULT_PAGE_SIZE, maxFileSize);
    return new IntFileAttributesStorage(vfs, storage);
  }

  public static void verifyVFSCreationTag(@NotNull FSRecordsImpl vfs,
                                          @NotNull IntFileAttributesStorage attributeStorage) throws IOException {
    long vfsCreationTag = vfs.getCreationTimestamp();
    if (attributeStorage.getVFSCreationTag() != vfsCreationTag) {
      attributeStorage.clear();
    }
    attributeStorage.setVFSCreationTag(vfsCreationTag);
  }

  public static @NotNull IntFileAttributesStorage openAndEnsureMatchVFS(@NotNull FSRecordsImpl vfs,
                                                                        @NotNull String name) throws IOException {
    IntFileAttributesStorage attributeStorage = openOrCreate(vfs, name);

    verifyVFSCreationTag(vfs, attributeStorage);

    return attributeStorage;
  }


  IntFileAttributesStorage(@NotNull FSRecordsImpl vfs,
                           @NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;

    var recordsStorage = vfs.connection().getRecords();
    maxAllocatedFileIdSupplier = () -> {
      return recordsStorage.maxAllocatedID();
    };
  }

  public int getVersion() throws IOException {
    return getIntHeaderField(HEADER_VERSION_OFFSET);
  }

  public void setVersion(int version) throws IOException {
    setIntHeaderField(HEADER_VERSION_OFFSET, version);
  }

  public long getVFSCreationTag() throws IOException {
    return getLongHeaderField(HEADER_VFS_CREATION_TIMESTAMP_OFFSET);
  }

  public void setVFSCreationTag(long vfsCreationTag) throws IOException {
    setLongHeaderField(HEADER_VFS_CREATION_TIMESTAMP_OFFSET, vfsCreationTag);
  }


  public int readAttribute(@NotNull VirtualFile vFile) throws IOException {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be VirtualFileWithId");
    }
    VirtualFileWithId fileWithId = (VirtualFileWithId)vFile;
    int fileId = fileWithId.getId();
    checkFileIdValid(fileId);

    return readAttribute(fileId);
  }

  public void writeAttribute(@NotNull VirtualFile vFile,
                             int value) throws IOException {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be VirtualFileWithId");
    }
    VirtualFileWithId fileWithId = (VirtualFileWithId)vFile;
    int fileId = fileWithId.getId();
    checkFileIdValid(fileId);

    writeAttribute(fileId, value);
  }

  public int updateAttribute(@NotNull VirtualFile vFile,
                             @NotNull IntUnaryOperator updateOperator) throws IOException {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be VirtualFileWithId");
    }
    VirtualFileWithId fileWithId = (VirtualFileWithId)vFile;
    int fileId = fileWithId.getId();
    checkFileIdValid(fileId);


    return updateAttribute(fileId, updateOperator);
  }

  public void clear() throws IOException {
    //MAYBE RC: it could be made much faster
    int maxAllocatedFileId = maxAllocatedFileID();
    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedFileId; fileId++) {
      writeAttribute(fileId, 0);
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

  @Override
  public String toString() {
    return "IntFileAttributesStorage[" + storage.storagePath() + "]";
  }


  /* ============== implementation: ====================================================================== */

  @VisibleForTesting
  int updateAttribute(int fileId,
                      @NotNull IntUnaryOperator updateOperator) throws IOException {
    int offsetInFile = toOffsetInFile(fileId);
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
  void writeAttribute(int fileId,
                      int attributeValue) throws IOException {
    int offsetInFile = toOffsetInFile(fileId);
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    INT_HANDLE.setVolatile(rawPageBuffer, offsetInPage, attributeValue);
  }

  @VisibleForTesting
  int readAttribute(int fileId) throws IOException {
    int offsetInFile = toOffsetInFile(fileId);
    int offsetInPage = storage.toOffsetInPage(offsetInFile);

    Page page = storage.pageByOffset(offsetInFile);
    ByteBuffer rawPageBuffer = page.rawPageBuffer();

    return (int)INT_HANDLE.getVolatile(rawPageBuffer, offsetInPage);
  }

  private void checkFileIdValid(int fileId) {
    int maxAllocatedID = maxAllocatedFileID();
    if (fileId < FSRecords.ROOT_FILE_ID || fileId > maxAllocatedID) {
      throw new IllegalArgumentException(
        "fileId[#" + fileId + "] is outside of allocated range [" + FSRecords.ROOT_FILE_ID + ".." + maxAllocatedID + "]");
    }
  }

  private int maxAllocatedFileID() {
    return maxAllocatedFileIdSupplier.getAsInt();
  }

  private int toOffsetInFile(int fileId) {
    return (fileId - FSRecords.ROOT_FILE_ID) * BYTES_PER_ATTRIBUTE + HEADER_SIZE;
  }

  private int getIntHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (int)INT_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private long getLongHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (long)LONG_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private void setIntHeaderField(int headerRelativeOffsetBytes,
                                 int headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      INT_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }

  private void setLongHeaderField(int headerRelativeOffsetBytes,
                                  long headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      LONG_HANDLE.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }
}
