// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Experimental API for faster access of file attribute if the attribute value is simple byte/int/long.
 * Some optimization could be applied in such scenarios,
 */
@ApiStatus.Internal
public final class SpecializedFileAttributes {
  //TODO RC: make consistent ByteOrder between ByteBuffer and (Input,Output)Stream access paths,
  //         so int written via specialized attribute could be read back via 'normal' FileAttribute.readAttribute()
  //         Write test for such consistency


  public static IntFileAttribute specializeAsInt(@NotNull FileAttribute attribute) {
    return specializeAsInt(FSRecords.getInstance(), attribute);
  }

  public static LongFileAttribute specializeAsLong(@NotNull FSRecordsImpl vfs,
                                                   @NotNull FileAttribute attribute) {
    if (!attribute.isFixedSize()) {
      throw new IllegalArgumentException(attribute + " must be fixedSize");
    }
    return new LongFileAttribute() {
      @Override
      public long read(@NotNull VirtualFile vFile,
                       long defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        return read(fileId, defaultValue);
      }

      @Override
      public void write(@NotNull VirtualFile vFile,
                        long value) throws IOException {
        int fileId = extractFileId(vFile);
        write(fileId, value);
      }

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
    };
  }

  public static IntFileAttribute specializeAsInt(@NotNull FSRecordsImpl vfs,
                                                 @NotNull FileAttribute attribute) {
    return new IntFileAttribute() {
      @Override
      public int read(@NotNull VirtualFile vFile,
                      int defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        return read(fileId, defaultValue);
      }

      @Override
      public int read(int fileId, int defaultValue) {
        Integer value = vfs.readAttributeRaw(fileId, attribute, ByteBuffer::getInt);
        return value == null ? defaultValue : value.intValue();
      }

      @Override
      public void write(@NotNull VirtualFile vFile,
                        int value) throws IOException {
        int fileId = extractFileId(vFile);
        write(fileId, value);
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
    };
  }

  public static ByteFileAttribute specializeAsByte(@NotNull FSRecordsImpl vfs,
                                                   @NotNull FileAttribute attribute) {
    return new ByteFileAttribute() {
      @Override
      public byte read(@NotNull VirtualFile vFile,
                       byte defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        return read(fileId, defaultValue);
      }

      @Override
      public byte read(int fileId,
                       byte defaultValue) {
        Byte value = vfs.readAttributeRaw(fileId, attribute, ByteBuffer::get);
        return value == null ? defaultValue : value.byteValue();
      }

      @Override
      public void write(@NotNull VirtualFile vFile,
                        byte value) throws IOException {
        int fileId = extractFileId(vFile);
        write(fileId, value);
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


  public static IntFileAttribute specializeAsFastInt(@NotNull FileAttribute attribute) throws IOException {
    return specializeAsFastInt(FSRecords.getInstance(), attribute);
  }

  public static IntFileAttribute specializeAsFastInt(@NotNull FSRecordsImpl vfs,
                                                     @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    //FIXME RC: who is responsible for closing the storage?

    IntFileAttributesStorage attributesStorage = IntFileAttributesStorage.openAndEnsureMatchVFS(vfs, attributeId);

    checkStorageVersion(vfs, attribute, attributesStorage);

    return new IntFileAttribute() {
      @Override
      public int read(@NotNull VirtualFile vFile,
                      int defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return attributesStorage.readAttribute(vFile);
      }

      @Override
      public int read(int fileId,
                      int defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return attributesStorage.readAttribute(fileId);
      }

      @Override
      public void write(@NotNull VirtualFile vFile, int value) throws IOException {
        attributesStorage.writeAttribute(vFile, value);
      }

      @Override
      public void write(int fileId, int value) throws IOException {
        attributesStorage.writeAttribute(fileId, value);
      }
    };
  }

  public static ShortFileAttribute specializeAsFastShort(@NotNull FSRecordsImpl vfs,
                                                         @NotNull FileAttribute attribute) throws IOException {
    String attributeId = attribute.getId();

    //FIXME RC: who is responsible for closing the storage?

    //FIXME RC: use actual ShortFileAttributesStorage

    IntFileAttributesStorage attributesStorage = IntFileAttributesStorage.openAndEnsureMatchVFS(vfs, attributeId);

    checkStorageVersion(vfs, attribute, attributesStorage);

    return new ShortFileAttribute() {
      @Override
      public short read(@NotNull VirtualFile vFile,
                        short defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return (short)attributesStorage.readAttribute(vFile);
      }

      @Override
      public short read(int fileId,
                        short defaultValue) throws IOException {
        if (defaultValue != 0) {
          throw new UnsupportedOperationException(
            "defaultValue=" + defaultValue + ": so far only 0 is supported default value for fast-attributes");
        }
        return (short)attributesStorage.readAttribute(fileId);
      }

      @Override
      public void write(@NotNull VirtualFile vFile,
                        short value) throws IOException {
        attributesStorage.writeAttribute(vFile, value);
      }

      @Override
      public void write(int fileId,
                        short value) throws IOException {
        attributesStorage.writeAttribute(fileId, value);
      }
    };
  }

  private static void checkStorageVersion(@NotNull FSRecordsImpl vfs,
                                          @NotNull FileAttribute attribute,
                                          @NotNull IntFileAttributesStorage attributesStorage) throws IOException {
    if (attribute.isVersioned()) {
      if (attributesStorage.getVersion() != attribute.getVersion()) {
        attributesStorage.clear();
        attributesStorage.setVFSCreationTag(vfs.getCreationTimestamp());
        attributesStorage.setVersion(attribute.getVersion());
      }
    }
  }

  //MAYBE RC: LongFileAttribute_Accessor_?
  public interface LongFileAttribute {
    long read(@NotNull VirtualFile vFile,
              long defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile,
               long value) throws IOException;

    long read(int fileId,
              long defaultValue) throws IOException;

    void write(int fileId,
               long value) throws IOException;
  }

  public interface IntFileAttribute {
    int read(@NotNull VirtualFile vFile, int defaultValue) throws IOException;

    int read(int fileId, int defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, int value) throws IOException;

    void write(int fileId, int value) throws IOException;
  }

  public interface ShortFileAttribute {
    short read(@NotNull VirtualFile vFile, short defaultValue) throws IOException;

    short read(int fileId, short defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, short value) throws IOException;

    void write(int fileId, short value) throws IOException;
  }

  public interface ByteFileAttribute {
    byte read(@NotNull VirtualFile vFile,
              byte defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, byte value) throws IOException;

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
