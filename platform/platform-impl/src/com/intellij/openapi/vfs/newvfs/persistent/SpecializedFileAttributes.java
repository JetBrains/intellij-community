// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * Experimental API for faster access of file attribute if the attribute value is simple
 * byte/short/int/long. Some optimization could be applied in such scenarios,
 */
@ApiStatus.Internal
public final class SpecializedFileAttributes {
  //TODO RC: using FileAttribute.id as file name of a storage is risky -- there is no guarantee that attribute id
  //         is a valid file name! Need to apply some character-escaping (risk different attributes names collide
  //         after escaping) or use enumerated attributeId for a file name instead of attribute.id (safe, but
  //         files in 'extended-attributes' become unrecognizable by human being)

  public static ByteFileAttributeAccessor specializeAsByte(@NotNull FileAttribute attribute) {
    return specializeAsByte(FSRecords.getInstance(), attribute);
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


  public static ShortFileAttributeAccessor specializeAsShort(@NotNull FileAttribute attribute) {
    return specializeAsShort(FSRecords.getInstance(), attribute);
  }

  public static ShortFileAttributeAccessor specializeAsShort(@NotNull FSRecordsImpl vfs,
                                                             @NotNull FileAttribute attribute) {
    return new ShortFileAttributeAccessor() {
      @Override
      public short read(int fileId, short defaultValue) {
        Short value = vfs.readAttributeRaw(fileId, attribute, buffer -> {
          //stream.writeShort() writes in BIG_ENDIAN (default byte order for JVM)
          return buffer.order(BIG_ENDIAN).getShort();
        });
        return value == null ? defaultValue : value.shortValue();
      }

      @Override
      public void write(int fileId,
                        short value) throws IOException {
        try (var stream = vfs.writeAttribute(fileId, attribute)) {
          stream.writeShort(value);
        }
      }
    };
  }


  public static IntFileAttributeAccessor specializeAsInt(@NotNull FileAttribute attribute) {
    return specializeAsInt(FSRecords.getInstance(), attribute);
  }

  public static IntFileAttributeAccessor specializeAsInt(@NotNull FSRecordsImpl vfs,
                                                         @NotNull FileAttribute attribute) {
    return new IntFileAttributeAccessor() {
      @Override
      public void close() {
        // noop
      }

      @Override
      public int read(int fileId, int defaultValue) {
        Integer value = vfs.readAttributeRaw(fileId, attribute, buffer -> {
          //stream.writeInt() writes in BIG_ENDIAN (default byte order for JVM)
          return buffer.order(BIG_ENDIAN).getInt();
        });
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

  public static LongFileAttributeAccessor specializeAsLong(@NotNull FileAttribute attribute) {
    return specializeAsLong(FSRecords.getInstance(), attribute);
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

        Long value = vfs.readAttributeRaw(fileId, attribute, buffer -> {
          //stream.writeLong() writes in BIG_ENDIAN (default byte order for JVM)
          return buffer.order(BIG_ENDIAN).getLong();
        });
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

      @Override
      public void close() {
        // noop
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

    return specializeAsFastLong(vfs, storageHelper);
  }

  private static @NotNull FastLongFileAttributeAccessor specializeAsFastLong(@NotNull FSRecordsImpl vfs, @NotNull MappedFileStorageHelper storageHelper) {
    FastLongFileAttributeAccessor accessor = new FastLongFileAttributeAccessor(storageHelper);
    vfs.addCloseable(accessor);
    vfs.addFileIdIndexedStorage(accessor);
    return accessor;
  }

  public static LongFileAttributeAccessor specializeAsFastLong(@NotNull FSRecordsImpl vfs,
                                                               @NotNull FileAttribute attribute,
                                                               @NotNull Path absolutePath) throws IOException {
    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs,
      absolutePath,
      attribute.getVersion(),
      Long.BYTES,
      true
    );

    return specializeAsFastLong(vfs, storageHelper);
  }

  public static IntFileAttributeAccessor specializeAsFastInt(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastInt(FSRecords.getInstance(), attribute);
  }

  public static IntFileAttributeAccessor specializeAsFastInt(@NotNull FSRecordsImpl vfs,
                                                             @NotNull FileAttribute attribute,
                                                             @NotNull Path absolutePath) throws IOException {
    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs,
      absolutePath,
      attribute.getVersion(),
      Integer.BYTES,
      true
    );

    return specializeAsFastInt(vfs, storageHelper);
  }

  public static IntFileAttributeAccessor specializeAsFastInt(@NotNull FSRecordsImpl vfs,
                                                             @NotNull FileAttribute attribute) throws IOException {
    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs,
      attribute.getId(),
      attribute.getVersion(),
      Integer.BYTES
    );

    return specializeAsFastInt(vfs, storageHelper);
  }

  private static IntFileAttributeAccessor specializeAsFastInt(@NotNull FSRecordsImpl vfs,
                                                              MappedFileStorageHelper storageHelper) throws IOException {
    FastIntFileAttributeAccessor accessor = new FastIntFileAttributeAccessor(storageHelper);
    vfs.addCloseable(accessor);
    vfs.addFileIdIndexedStorage(accessor);
    return accessor;
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

    FastShortFileAttributeAccessor accessor = new FastShortFileAttributeAccessor(storageHelper);
    vfs.addCloseable(accessor);
    vfs.addFileIdIndexedStorage(accessor);
    return accessor;
  }

  public static ByteFileAttributeAccessor specializeAsFastByte(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastByte(FSRecords.getInstance(), attribute);
  }

  public static ByteFileAttributeAccessor specializeAsFastByte(@NotNull FSRecordsImpl vfs,
                                                               @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    //RC: true byte attribute is impossible to implement since VarHandle(byte[]) is not supported by JDK -- hence
    //    we actually use int16 (short) fields
    MappedFileStorageHelper storageHelper = MappedFileStorageHelper.openHelperAndVerifyVersions(
      vfs, attributeId, attribute.getVersion(), Short.BYTES
    );

    FastByteFileAttributeAccessor accessor = new FastByteFileAttributeAccessor(storageHelper);
    vfs.addCloseable(accessor);
    vfs.addFileIdIndexedStorage(accessor);
    return accessor;
  }


  public interface LongFileAttributeAccessor extends Closeable {
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

  public interface IntFileAttributeAccessor extends Closeable {
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


  private static int extractFileId(@NotNull VirtualFile vFile) {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be instance of VirtualFileWithId");
    }
    return ((VirtualFileWithId)vFile).getId();
  }


  /** Advanced-level control over fast attributes, for expert to use */
  public interface FileAttributeAccessorEx extends FSRecordsImpl.FileIdIndexedStorage {

    @Override
    void clear(int fileId) throws IOException;

    /**
     * Clear (set to default) all the records. I.e. conceptually it is the same as
     * {@code forEach(fileId): clear(fileId) }
     * BEWARE: this method is not atomic in a multithreaded environment, i.e. it shouldn't be called in a race with
     * concurrent updates -- results are undefined.
     */
    void clear() throws IOException;

    void flush() throws IOException;
  }

  private abstract static class FastAttributeAccessorHelper implements FileAttributeAccessorEx, Closeable, Unmappable, CleanableStorage {
    protected final @NotNull MappedFileStorageHelper helper;


    private FastAttributeAccessorHelper(@NotNull MappedFileStorageHelper helper) { this.helper = helper; }

    @Override
    public void clear() throws IOException {
      helper.clearRecords();
    }

    @Override
    public void flush() throws IOException {
      if (MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT) {
        helper.fsync();
      }
    }

    @Override
    public void close() throws IOException {
      helper.close();
    }

    @Override
    public void closeAndUnsafelyUnmap() throws IOException {
      helper.closeAndUnsafelyUnmap();
    }

    @Override
    public void closeAndClean() throws IOException {
      helper.closeAndClean();
    }
  }


  private static class FastLongFileAttributeAccessor extends FastAttributeAccessorHelper implements LongFileAttributeAccessor {
    private static final int FIELD_OFFSET = 0;

    private FastLongFileAttributeAccessor(@NotNull MappedFileStorageHelper storageHelper) {
      super(storageHelper);
    }

    @Override
    public long read(int fileId,
                     long defaultValue) throws IOException {
      if (defaultValue != 0) {
        throw new UnsupportedOperationException(
          "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
      }
      return helper.readLongField(fileId, FIELD_OFFSET);
    }

    @Override
    public void write(int fileId,
                      long value) throws IOException {
      helper.writeLongField(fileId, FIELD_OFFSET, value);
    }

    @Override
    public void update(int fileId,
                       @NotNull LongUnaryOperator updater) throws IOException {
      helper.updateLongField(fileId, FIELD_OFFSET, updater);
    }

    @Override
    public void clear(int fileId) throws IOException {
      helper.writeLongField(fileId, FIELD_OFFSET, 0L);
    }
  }

  private static class FastIntFileAttributeAccessor extends FastAttributeAccessorHelper implements IntFileAttributeAccessor {
    private static final int FIELD_OFFSET = 0;

    private FastIntFileAttributeAccessor(@NotNull MappedFileStorageHelper storageHelper) {
      super(storageHelper);
    }

    @Override
    public int read(int fileId,
                    int defaultValue) throws IOException {
      if (defaultValue != 0) {
        throw new UnsupportedOperationException(
          "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
      }
      return helper.readIntField(fileId, FIELD_OFFSET);
    }

    @Override
    public void write(int fileId, int value) throws IOException {
      helper.writeIntField(fileId, FIELD_OFFSET, value);
    }

    @Override
    public void update(int fileId,
                       @NotNull IntUnaryOperator updater) throws IOException {
      helper.updateIntField(fileId, FIELD_OFFSET, updater);
    }

    @Override
    public void clear(int fileId) throws IOException {
      helper.writeIntField(fileId, FIELD_OFFSET, 0);
    }
  }

  private static class FastShortFileAttributeAccessor extends FastAttributeAccessorHelper implements ShortFileAttributeAccessor {
    private static final int FIELD_OFFSET = 0;

    private FastShortFileAttributeAccessor(@NotNull MappedFileStorageHelper helper) {
      super(helper);
    }

    @Override
    public short read(int fileId,
                      short defaultValue) throws IOException {
      if (defaultValue != 0) {
        throw new UnsupportedOperationException(
          "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
      }
      return helper.readShortField(fileId, FIELD_OFFSET);
    }

    @Override
    public void write(int fileId,
                      short value) throws IOException {
      helper.writeShortField(fileId, FIELD_OFFSET, value);
    }

    @Override
    public void clear(int fileId) throws IOException {
      helper.writeShortField(fileId, FIELD_OFFSET, (short)0);
    }
  }

  //RC: true byte attribute is impossible to implement since VarHandle(byte[]) is not supported by JDK -- hence
  //    we actually use int16 (short) fields
  private static class FastByteFileAttributeAccessor extends FastAttributeAccessorHelper implements ByteFileAttributeAccessor {
    private static final int FIELD_OFFSET = 0;

    private FastByteFileAttributeAccessor(@NotNull MappedFileStorageHelper helper) {
      super(helper);
      if (helper.bytesPerRow() != Short.BYTES) {
        throw new AssertionError("Bug: helper must have 2 bytes per row, not " + helper.bytesPerRow());
      }
    }

    @Override
    public byte read(int fileId,
                     byte defaultValue) throws IOException {
      if (defaultValue != 0) {
        throw new UnsupportedOperationException(
          "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
      }
      return (byte)helper.readShortField(fileId, FIELD_OFFSET);
    }

    @Override
    public void write(int fileId,
                      byte value) throws IOException {
      helper.writeShortField(fileId, FIELD_OFFSET, value);
    }

    @Override
    public void clear(int fileId) throws IOException {
      helper.writeShortField(fileId, FIELD_OFFSET, (short)0);
    }
  }
}
