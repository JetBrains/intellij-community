// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.FileIdIndexedStorage;
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.IntUnaryOperator;

import static com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper.openHelperAndVerifyVersions;

/**
 * (Temporary) Different non-standard kinds of VirtualFile attributes.
 * Standard attributes are {@link FileAttribute} and {@link SpecializedFileAttributes},
 * and here are some more exotic kinds, which may or may not be useful.
 */
@ApiStatus.Internal
public final class FastFileAttributes {

  private FastFileAttributes() {
    throw new AssertionError("Not for instantiation");
  }

  public static @NotNull Int4FileAttribute int4FileAttributes(@NotNull FSRecordsImpl vfs,
                                                              @NotNull String storageName,
                                                              int version) throws IOException {
    MappedFileStorageHelper helper = openHelperAndVerifyVersions(
      vfs,
      storageName,
      version,
      Int4FileAttribute.ROW_SIZE
    );

    Int4FileAttribute attribute = new Int4FileAttribute(helper);
    vfs.addCloseable(attribute);
    vfs.addFileIdIndexedStorage(attribute);
    return attribute;
  }

  public static TimestampedBooleanAttributeAccessor timestampedBoolean(@NotNull FileAttribute attribute) throws IOException {
    return timestampedBoolean(FSRecords.getInstance(), attribute);
  }

  public static TimestampedBooleanAttributeAccessor timestampedBoolean(@NotNull FSRecordsImpl vfs,
                                                                       @NotNull FileAttribute attribute) throws IOException {
    MappedFileStorageHelper helper = openHelperAndVerifyVersions(
      vfs,
      attribute.getId(),
      attribute.getVersion(),
      TimestampedBooleanAttributeAccessorImpl.ROW_SIZE
    );


    TimestampedBooleanAttributeAccessorImpl accessor = new TimestampedBooleanAttributeAccessorImpl(helper);
    vfs.addCloseable(accessor);
    vfs.addFileIdIndexedStorage(accessor);
    return accessor;
  }

  public static final class Int4FileAttribute implements FileIdIndexedStorage, Closeable, Unmappable, CleanableStorage {
    public static final int FIELDS = 4;
    public static final int ROW_SIZE = FIELDS * Integer.BYTES;

    private final MappedFileStorageHelper storageHelper;

    public Int4FileAttribute(@NotNull MappedFileStorageHelper helper) { storageHelper = helper; }

    public int readField(@NotNull VirtualFile vFile,
                         int fieldNo) throws IOException {
      return storageHelper.readIntField(vFile, fieldOffset(fieldNo));
    }

    public int readField(int fileId,
                         int fieldNo) throws IOException {
      return storageHelper.readIntField(fileId, fieldOffset(fieldNo));
    }

    public void write(@NotNull VirtualFile vFile,
                      int fieldNo,
                      int value) throws IOException {
      storageHelper.writeIntField(vFile, fieldOffset(fieldNo), value);
    }

    public void write(int fileId,
                      int fieldNo,
                      int value) throws IOException {
      storageHelper.writeIntField(fileId, fieldOffset(fieldNo), value);
    }

    public void update(@NotNull VirtualFile vFile,
                       int fieldNo,
                       @NotNull IntUnaryOperator updater) throws IOException {
      storageHelper.updateIntField(vFile, fieldOffset(fieldNo), updater);
    }

    @Override
    public void clear(int fileId) throws IOException {
      storageHelper.writeIntField(fileId, 0, 0);
      storageHelper.writeIntField(fileId, 1, 0);
      storageHelper.writeIntField(fileId, 2, 0);
      storageHelper.writeIntField(fileId, 3, 0);
    }

    private static int fieldOffset(int fieldNo) {
      if (fieldNo < 0 || fieldNo >= FIELDS) {
        throw new IllegalArgumentException("fieldNo(=" + fieldNo + ") must be in [0," + FIELDS + ")");
      }
      return fieldNo * Integer.BYTES;
    }

    @Override
    public void close() throws IOException {
      storageHelper.close();
    }

    @Override
    public void closeAndUnsafelyUnmap() throws IOException {
      storageHelper.closeAndUnsafelyUnmap();
    }

    @Override
    public void closeAndClean() throws IOException {
      storageHelper.closeAndClean();
    }
  }

  private static final class TimestampedBooleanAttributeAccessorImpl
    implements TimestampedBooleanAttributeAccessor, FileIdIndexedStorage,
               Unmappable, CleanableStorage, Closeable {
    public static final int ROW_SIZE = Long.BYTES;

    private final MappedFileStorageHelper storageHelper;

    private TimestampedBooleanAttributeAccessorImpl(MappedFileStorageHelper helper) { storageHelper = helper; }

    @Override
    public @Nullable Boolean readIfActual(VirtualFile vFile) throws IOException {
      long stamp = vFile.getTimeStamp();
      long fieldValue = storageHelper.readLongField(vFile, 0);

      //use sign bit to store true(1) | false(0)
      long timestamp = Math.abs(fieldValue);
      boolean value = fieldValue < 0;

      if (timestamp == stamp) {
        return value;
      }
      else {
        return null;
      }
    }

    @Override
    public void write(VirtualFile vFile, boolean value) throws IOException {
      long stamp = vFile.getTimeStamp();
      //use sign bit to store true(1) | false(0)
      long fieldValue = value ? -stamp : stamp;
      storageHelper.writeLongField(vFile, 0, fieldValue);
    }

    @Override
    public void clear(int fileId) throws IOException {
      storageHelper.writeLongField(fileId, 0, 0);
    }

    @Override
    public void close() throws IOException {
      storageHelper.close();
    }

    @Override
    public void closeAndUnsafelyUnmap() throws IOException {
      storageHelper.closeAndUnsafelyUnmap();
    }

    @Override
    public void closeAndClean() throws IOException {
      storageHelper.closeAndClean();
    }
  }

  public interface TimestampedBooleanAttributeAccessor {
    @Nullable Boolean readIfActual(VirtualFile vFile) throws IOException;

    void write(VirtualFile vFile,
               boolean value) throws IOException;
  }
}
