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


  public static LongFileAttribute specializeAsLong(@NotNull FileAttribute attribute) {
    if (!attribute.isFixedSize()) {
      throw new IllegalArgumentException(attribute + " must be fixedSize");
    }
    return new LongFileAttribute() {
      @Override
      public long read(@NotNull VirtualFile vFile,
                       long defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        Long value = FSRecords.readAttributeRawWithLock(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            int version = buffer.getInt();
            if (version != attribute.getVersion()) {
              return null;
            }
          }
          return buffer.getLong();
        });
        return value == null ? defaultValue : value.longValue();
      }

      @Override
      public void write(@NotNull VirtualFile vFile, long value) throws IOException {
        int fileId = extractFileId(vFile);
        FSRecords.writeAttributeRaw(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            buffer.putInt(attribute.getVersion());
          }
          return buffer.putLong(value);
        });
      }
    };
  }


  public static IntFileAttribute specializeAsInt(@NotNull FileAttribute attribute) {
    return new IntFileAttribute() {
      @Override
      public int read(@NotNull VirtualFile vFile,
                      int defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        Integer value = FSRecords.readAttributeRawWithLock(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            int version = buffer.getInt();
            if (version != attribute.getVersion()) {
              return null;
            }
          }
          return buffer.getInt();
        });
        return value == null ? defaultValue : value.intValue();
      }

      @Override
      public void write(@NotNull VirtualFile vFile, int value) throws IOException {
        int fileId = extractFileId(vFile);
        FSRecords.writeAttributeRaw(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            buffer.putInt(attribute.getVersion());
          }
          return buffer.putInt(value);
        });
      }
    };
  }

  public static ByteFileAttribute specializeAsByte(@NotNull FileAttribute attribute) {
    return new ByteFileAttribute() {
      @Override
      public byte read(@NotNull VirtualFile vFile,
                       byte defaultValue) throws IOException {
        int fileId = extractFileId(vFile);
        Byte value = FSRecords.readAttributeRawWithLock(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            int version = buffer.getInt();
            if (version != attribute.getVersion()) {
              return null;
            }
          }
          return buffer.get();
        });
        return value == null ? defaultValue : value.byteValue();
      }

      @Override
      public void write(@NotNull VirtualFile vFile, byte value) throws IOException {
        int fileId = extractFileId(vFile);
        FSRecords.writeAttributeRaw(fileId, attribute, buffer -> {
          if (attribute.isVersioned()) {
            buffer.putInt(attribute.getVersion());
          }
          return buffer.put(value);
        });
      }
    };
  }


  public interface LongFileAttribute {
    long read(@NotNull VirtualFile vFile,
              long defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, long value) throws IOException;
  }

  public interface IntFileAttribute {
    int read(@NotNull VirtualFile vFile,
             int defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, int value) throws IOException;
  }

  public interface ByteFileAttribute {
    byte read(@NotNull VirtualFile vFile,
              byte defaultValue) throws IOException;

    void write(@NotNull VirtualFile vFile, byte value) throws IOException;
  }

  private static int extractFileId(@NotNull VirtualFile vFile) {
    if (!(vFile instanceof VirtualFileWithId)) {
      throw new IllegalArgumentException(vFile + " must be instance of VirtualFileWithId");
    }
    return ((VirtualFileWithId)vFile).getId();
  }
}
