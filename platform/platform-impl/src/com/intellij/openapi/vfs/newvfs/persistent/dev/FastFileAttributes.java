// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntUnaryOperator;

/**
 * Temporary
 */
@ApiStatus.Internal
public final class FastFileAttributes {

  private FastFileAttributes() {
    throw new AssertionError("Not for instantiation");
  }

  private static @NotNull MappedFileStorageHelper openHelper(@NotNull FSRecordsImpl vfs,
                                                             @NotNull String storageName,
                                                             int bytesPerRow) throws IOException {
    PersistentFSConnection connection = vfs.connection();
    Path fastAttributesDir = connection.getPersistentFSPaths().storagesSubDir("extended-attributes");
    Files.createDirectories(fastAttributesDir);

    Path storagePath = fastAttributesDir.resolve(storageName);
    long maxFileSize = MappedFileStorageHelper.HeaderLayout.HEADER_SIZE + bytesPerRow * (long)Integer.MAX_VALUE;
    MMappedFileStorage storage = new MMappedFileStorage(
      storagePath,
      MappedFileStorageHelper.DEFAULT_PAGE_SIZE,
      maxFileSize
    );
    var recordsStorage = connection.getRecords();
    return new MappedFileStorageHelper(storage, bytesPerRow, recordsStorage::maxAllocatedID);
  }

  private static @NotNull MappedFileStorageHelper openHelperAndVerifyMatchVFS(@NotNull FSRecordsImpl vfs,
                                                                              @NotNull String storageName,
                                                                              int bytesPerRow) throws IOException {
    MappedFileStorageHelper helper = openHelper(vfs, storageName, bytesPerRow);

    verifyVFSCreationTag(vfs, helper);

    return helper;
  }

  static void verifyVFSCreationTag(@NotNull FSRecordsImpl vfs,
                                   @NotNull MappedFileStorageHelper helper) throws IOException {
    long vfsCreationTag = vfs.getCreationTimestamp();
    if (helper.getVFSCreationTag() != vfsCreationTag) {
      helper.clear();
    }
    helper.setVFSCreationTag(vfsCreationTag);
  }

  public static IntFileAttribute intFileAttributes(@NotNull FSRecordsImpl vfs,
                                                   @NotNull String storageName) throws IOException {
    MappedFileStorageHelper helper = openHelperAndVerifyMatchVFS(vfs, storageName, IntFileAttribute.ROW_SIZE);
    return new IntFileAttribute(helper);
  }

  public static Int3FileAttribute int3FileAttributes(@NotNull FSRecordsImpl vfs,
                                                     @NotNull String storageName) throws IOException {
    MappedFileStorageHelper helper = openHelperAndVerifyMatchVFS(vfs, storageName, Int3FileAttribute.ROW_SIZE);
    return new Int3FileAttribute(helper);
  }


  public static class IntFileAttribute implements SpecializedFileAttributes.IntFileAttribute, Closeable {
    public static final int ROW_SIZE = Integer.BYTES;
    public static final int FIELD_OFFSET = 0;

    private final MappedFileStorageHelper storageHelper;

    public IntFileAttribute(@NotNull MappedFileStorageHelper helper) { storageHelper = helper; }

    @Override
    public int read(@NotNull VirtualFile vFile, int defaultValue) throws IOException {
      if (defaultValue != 0) {
        throw new UnsupportedOperationException(
          "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
      }
      return storageHelper.readIntField(vFile, FIELD_OFFSET);
    }

    @Override
    public void write(@NotNull VirtualFile vFile,
                      int value) throws IOException {
      storageHelper.writeIntField(vFile, FIELD_OFFSET, value);
    }

    @Override
    public int read(int fileId, int defaultValue) throws IOException {
      return storageHelper.readIntField(fileId,FIELD_OFFSET);
    }

    @Override
    public void write(int fileId, int value) throws IOException {
      storageHelper.writeIntField(fileId, FIELD_OFFSET, value);
    }

    public void update(@NotNull VirtualFile vFile,
                       @NotNull IntUnaryOperator updater) throws IOException {
      storageHelper.updateIntField(vFile, FIELD_OFFSET, updater);
    }

    public void fsync() throws IOException {
      storageHelper.fsync();
    }

    @Override
    public void close() throws IOException {
      storageHelper.close();
    }
  }

  public static class Int3FileAttribute implements Closeable {
    public static final int FIELDS = 3;
    public static final int ROW_SIZE = FIELDS * Integer.BYTES;

    private final MappedFileStorageHelper storageHelper;

    public Int3FileAttribute(@NotNull MappedFileStorageHelper helper) { storageHelper = helper; }

    public int readField(@NotNull VirtualFile vFile,
                         int fieldNo) throws IOException {
      return storageHelper.readIntField(vFile, fieldOffset(fieldNo));
    }

    public void write(@NotNull VirtualFile vFile,
                      int fieldNo,
                      int value) throws IOException {
      storageHelper.writeIntField(vFile, fieldOffset(fieldNo), value);
    }

    public void update(@NotNull VirtualFile vFile,
                       int fieldNo,
                       @NotNull IntUnaryOperator updater) throws IOException {
      storageHelper.updateIntField(vFile, fieldOffset(fieldNo), updater);
    }

    public void fsync() throws IOException {
      storageHelper.fsync();
    }

    private static int fieldOffset(int fieldNo) {
      if (fieldNo < 0 || fieldNo >= FIELDS) {
        throw new IllegalArgumentException("fieldNo(=" + fieldNo + ") must be in [0,3)");
      }
      return fieldNo * Integer.BYTES;
    }

    @Override
    public void close() throws IOException {
      storageHelper.close();
    }

    public void closeAndRemove() throws IOException {
      storageHelper.closeAndRemove();
    }
  }
}
