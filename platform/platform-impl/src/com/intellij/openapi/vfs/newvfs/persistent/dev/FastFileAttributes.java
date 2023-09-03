// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
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

  public static Int3FileAttribute int3FileAttributes(@NotNull FSRecordsImpl vfs,
                                                     @NotNull String storageName,
                                                     int version) throws IOException {
    MappedFileStorageHelper helper = openHelperAndVerifyVersions(
      vfs,
      storageName,
      Int3FileAttribute.ROW_SIZE,
      version
    );

    vfs.addCloseable(helper);

    return new Int3FileAttribute(helper);
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

    vfs.addCloseable(helper);

    return new TimestampedBooleanAttributeAccessorImpl(helper);
  }

  public static final class Int3FileAttribute implements Closeable {
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

  private static final class TimestampedBooleanAttributeAccessorImpl implements TimestampedBooleanAttributeAccessor, Closeable {
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
    public void close() throws IOException {
      storageHelper.close();
    }
  }

  public interface TimestampedBooleanAttributeAccessor {
    @Nullable Boolean readIfActual(VirtualFile vFile) throws IOException;

    void write(VirtualFile vFile,
               boolean value) throws IOException;
  }
}
