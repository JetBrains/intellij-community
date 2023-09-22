// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * Experimental API for faster access of file attribute if the attribute value is simple
 * byte/short/int/long. Some optimization could be applied in such scenarios,
 */
@ApiStatus.Internal
public final class SpecializedFileAttributes {
  //TODO RC: make consistent ByteOrder between ByteBuffer and (Input,Output)Stream access paths,
  //         so int written via specialized attribute could be read back via 'normal' FileAttribute.readAttribute()
  //         Write test for such consistency

  //TODO RC: using FileAttribute.id as file name of a storage is risky -- there is no guarantee that attribute id
  //         is a valid file name! Need to apply some character-escaping (risk different attributes names collide
  //         after escaping) or use enumerated attributeId for a file name instead of attribute.id (safe, but
  //         files in 'extended-storages' become unrecognizable by human being)

  public static IntFileAttributeAccessor specializeAsInt(@NotNull FileAttribute attribute) {
    return specializeAsInt(FSRecords.getInstance(), attribute);
  }

  public static LongFileAttributeAccessor specializeAsLong(@NotNull FSRecordsImpl vfs,
                                                           @NotNull FileAttribute attribute) {
    if (!attribute.isFixedSize()) {
      throw new IllegalArgumentException(attribute + " must be fixedSize");
    }
    return new LongFileAttributeAccessor() {
      @Override
      public long read(int fileId,
                       long defaultValue) throws IOException {
        Long value = vfs.readAttributeRaw(fileId, attribute, ByteBuffer::getLong);
        return value == null ? defaultValue : value.longValue();
      }

      @Override
      public void write(int fileId,
                        long value) throws IOException {
        try (var stream = vfs.writeAttribute(fileId, attribute)) {
          stream.writeLong(value);
        }
        //vfs.writeAttributeRaw(fileId, attribute, buffer -> {
        //  return buffer.putLong(value);
        //});
      }

      @Override
      public void update(int fileId, @NotNull LongUnaryOperator updater) throws IOException {
        throw new UnsupportedOperationException("Method is not implemented");
      }
    };
  }

  public static IntFileAttributeAccessor specializeAsInt(@NotNull FSRecordsImpl vfs,
                                                         @NotNull FileAttribute attribute) {
    return new IntFileAttributeAccessor() {
      @Override
      public int read(int fileId, int defaultValue) {
        Integer value = vfs.readAttributeRaw(fileId, attribute, ByteBuffer::getInt);
        return value == null ? defaultValue : value.intValue();
      }

      @Override
      public void write(int fileId,
                        int value) throws IOException {
        try (var stream = vfs.writeAttribute(fileId, attribute)) {
          stream.writeInt(value);
        }
        //vfs.writeAttributeRaw(fileId, attribute, buffer -> {
        //  if (attribute.isVersioned()) {
        //    buffer.putInt(attribute.getVersion());
        //  }
        //  return buffer.putInt(value);
        //});
      }

      @Override
      public void update(int fileId, @NotNull IntUnaryOperator updater) throws IOException {
        throw new UnsupportedOperationException("Method not implemented yet");
      }
    };
  }

  public static ByteFileAttributeAccessor specializeAsByte(@NotNull FSRecordsImpl vfs,
                                                           @NotNull FileAttribute attribute) {
    return new ByteFileAttributeAccessor() {

      @Override
      public byte read(int fileId,
                       byte defaultValue) {
        Byte value = vfs.readAttributeRaw(fileId, attribute, ByteBuffer::get);
        return value == null ? defaultValue : value.byteValue();
      }

      @Override
      public void write(int fileId,
                        byte value) throws IOException {
        try (var stream = vfs.writeAttribute(fileId, attribute)) {
          stream.write(value);
        }
        //vfs.writeAttributeRaw(fileId, attribute, buffer -> {
        //  return buffer.put(value);
        //});
      }
    };
  }


  public static LongFileAttributeAccessor specializeAsFastLong(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastLong(FSRecords.getInstance(), attribute);
  }

  public static LongFileAttributeAccessor specializeAsFastLong(@NotNull FSRecordsImpl vfs,
                                                               @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs,
      attributeId,
      attribute.getVersion(),
      Long.BYTES
    );
    vfs.addCloseable(storageHelper);

    int fieldOffset = 0;

    return new LongFileAttributeAccessor() {
      @Override
      public long read(int fileId,
                       long defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return storageHelper.readLongField(fileId, fieldOffset);
      }

      @Override
      public void write(int fileId,
                        long value) throws IOException {
        storageHelper.writeLongField(fileId, fieldOffset, value);
      }

      @Override
      public void update(int fileId,
                         @NotNull LongUnaryOperator updater) throws IOException {
        storageHelper.updateLongField(fileId, fieldOffset, updater);
      }
    };
  }

  public static IntFileAttributeAccessor specializeAsFastInt(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastInt(FSRecords.getInstance(), attribute);
  }

  public static IntFileAttributeAccessor specializeAsFastInt(@NotNull FSRecordsImpl vfs,
                                                             @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs,
      attributeId,
      attribute.getVersion(),
      Integer.BYTES
    );
    vfs.addCloseable(storageHelper);

    int fieldOffset = 0;

    return new IntFileAttributeAccessor() {

      @Override
      public int read(int fileId,
                      int defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return storageHelper.readIntField(fileId, fieldOffset);
      }

      @Override
      public void write(int fileId, int value) throws IOException {
        storageHelper.writeIntField(fileId, fieldOffset, value);
      }

      @Override
      public void update(int fileId,
                         @NotNull IntUnaryOperator updater) throws IOException {
        storageHelper.updateIntField(fileId, fieldOffset, updater);
      }
    };
  }


  public static ShortFileAttributeAccessor specializeAsFastShort(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastShort(FSRecords.getInstance(), attribute);
  }

  public static ShortFileAttributeAccessor specializeAsFastShort(@NotNull FSRecordsImpl vfs,
                                                                 @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs, attributeId, attribute.getVersion(), Short.BYTES
    );
    vfs.addCloseable(storageHelper);

    int fieldOffset = 0;
    return new ShortFileAttributeAccessor() {
      @Override
      public short read(int fileId,
                        short defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return storageHelper.readShortField(fileId, fieldOffset);
      }

      @Override
      public void write(int fileId,
                        short value) throws IOException {
        storageHelper.writeShortField(fileId, fieldOffset, value);
      }
    };
  }

  public static ByteFileAttributeAccessor specializeAsFastByte(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastByte(FSRecords.getInstance(), attribute);
  }

  public static ByteFileAttributeAccessor specializeAsFastByte(@NotNull FSRecordsImpl vfs,
                                                               @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    //TODO RC: true int8 implementation is not available so far, use int16
    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs, attributeId, attribute.getVersion(), Short.BYTES
    );
    vfs.addCloseable(storageHelper);

    int fieldOffset = 0;
    return new ByteFileAttributeAccessor() {
      @Override
      public byte read(int fileId,
                       byte defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return (byte)storageHelper.readShortField(fileId, fieldOffset);
      }

      @Override
      public void write(int fileId,
                        byte value) throws IOException {
        storageHelper.writeShortField(fileId, fieldOffset, value);
      }
    };
  }


  public interface LongFileAttributeAccessor {
    default long read(@NotNull VirtualFile vFile) throws IOException {
      return read(vFile, 0);
    }

    default long read(@NotNull VirtualFile vFile,
                      long defaultValue) throws IOException {
      return read(extractFileId(vFile), defaultValue);
    }

    default void write(@NotNull VirtualFile vFile,
                       long value) throws IOException {
      write(extractFileId(vFile), value);
    }

    default void update(@NotNull VirtualFile vFile,
                        @NotNull LongUnaryOperator updater) throws IOException {
      update(extractFileId(vFile), updater);
    }

    long read(int fileId,
              long defaultValue) throws IOException;

    void write(int fileId,
               long value) throws IOException;

    void update(int fileId,
                @NotNull LongUnaryOperator updater) throws IOException;
  }

  public interface IntFileAttributeAccessor {
    default int read(@NotNull VirtualFile vFile) throws IOException {
      return read(vFile, 0);
    }

    default int read(@NotNull VirtualFile vFile, int defaultValue) throws IOException {
      return read(extractFileId(vFile), defaultValue);
    }

    default void write(@NotNull VirtualFile vFile, int value) throws IOException {
      write(extractFileId(vFile), value);
    }

    default void update(@NotNull VirtualFile vFile,
                        @NotNull IntUnaryOperator updater) throws IOException {
      update(extractFileId(vFile), updater);
    }

    int read(@Range(from = 1, to = Integer.MAX_VALUE) int fileId, int defaultValue) throws IOException;

    void write(@Range(from = 1, to = Integer.MAX_VALUE) int fileId, int value) throws IOException;

    void update(@Range(from = 1, to = Integer.MAX_VALUE) int fileId,
                @NotNull IntUnaryOperator updater) throws IOException;
  }

  public interface ShortFileAttributeAccessor {
    default short read(@NotNull VirtualFile vFile) throws IOException {
      return read(vFile, (short)0);
    }

    default short read(@NotNull VirtualFile vFile, short defaultValue) throws IOException {
      return read(extractFileId(vFile), defaultValue);
    }

    default void write(@NotNull VirtualFile vFile, short value) throws IOException {
      write(extractFileId(vFile), value);
    }

    short read(int fileId, short defaultValue) throws IOException;

    void write(int fileId, short value) throws IOException;
  }

  public interface ByteFileAttributeAccessor {

    default byte read(@NotNull VirtualFile vFile) throws IOException {
      return read(vFile, (byte)0);
    }

    default byte read(@NotNull VirtualFile vFile,
                      byte defaultValue) throws IOException {
      return read(extractFileId(vFile), defaultValue);
    }

    default void write(@NotNull VirtualFile vFile,
                       byte value) throws IOException {
      write(extractFileId(vFile), value);
    }

    byte read(int fileId,
              byte defaultValue) throws IOException;

    void write(int fileId,
               byte value) throws IOException;
  }


  //TODO RC: make 'Fast' accessors implement this interface also, so clients who need
  //         more control over impl -- could have it
  public interface FileAttributeExAccessor {
    void clear() throws IOException;

    void flush() throws IOException;
  }

  private static int extractFileId(@NotNull VirtualFile vFile) {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be instance of VirtualFileWithId");
    }
    return ((VirtualFileWithId)vFile).getId();
  }
}
