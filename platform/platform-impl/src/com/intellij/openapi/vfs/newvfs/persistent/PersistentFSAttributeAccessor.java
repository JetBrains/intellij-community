// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@ApiStatus.Internal
public final class PersistentFSAttributeAccessor {

  private final @NotNull PersistentFSConnection connection;
  private final @NotNull AbstractAttributesStorage attributesStorage;

  PersistentFSAttributeAccessor(final @NotNull PersistentFSConnection connection) {
    this.connection = connection;
    attributesStorage = connection.getAttributes();
  }

  public boolean hasAttributePage(final int fileId,
                                  final @NotNull FileAttribute attribute) throws IOException {
    connection.ensureFileIdIsValid(fileId);
    return attributesStorage.hasAttributePage(connection, fileId, attribute);
  }

  public @Nullable AttributeInputStream readAttribute(final int fileId,
                                                      final @NotNull FileAttribute attribute) throws IOException {
    final AttributeInputStream attributeStream = attributesStorage.readAttribute(connection, fileId, attribute);
    return validateAttributeVersion(attribute, attributeStream);
  }

  @ApiStatus.Internal
  public static @Nullable AttributeInputStream validateAttributeVersion(final @NotNull FileAttribute attribute,
                                                                        final AttributeInputStream attributeStream) {
    if (attributeStream != null && attribute.isVersioned()) {
      try {
        final int actualVersion = DataInputOutputUtil.readINT(attributeStream);
        if (actualVersion != attribute.getVersion()) {
          return null;
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    return attributeStream;
  }

  //======== RC: methods to access attributesStorage raw byteBuffer, if storage supports it

  boolean supportsRawAccess() {
    return attributesStorage instanceof AttributesStorageOverBlobStorage;
  }

  /**
   * Method to read attribute content, alternative to {@link #readAttribute(int, FileAttribute)} -- provides access
   * to underlying {@link java.nio.ByteBuffer}, instead of {@link AttributeInputStream}.
   * Check {@link #supportsRawAccess()} before call -- method throws {@link UnsupportedOperationException} otherwise.
   *
   * @return null if an appropriate attribute record does not exist
   */
  <R> @Nullable R readAttributeRaw(final int fileId,
                                   final @NotNull FileAttribute attribute,
                                   final ByteBufferReader<R> reader) throws IOException {
    if (!(attributesStorage instanceof AttributesStorageOverBlobStorage newAttributesStorage)) {
      throw new UnsupportedOperationException("Raw attribute access is not implemented for " + attributesStorage.getClass().getName());
    }

    connection.ensureFileIdIsValid(fileId);

    return newAttributesStorage.readAttributeRaw(connection, fileId, attribute, buffer -> {
      if (attribute.isVersioned()) {
        final int actualVersion = DataInputOutputUtil.readINT(buffer);
        if (actualVersion != attribute.getVersion()) {
          return null;
        }
      }
      return reader.read(buffer);
    });
  }

  public void writeAttributeRaw(final int fileId,
                                final @NotNull FileAttribute attribute,
                                final ByteBufferWriter writer) {
    if (!(attributesStorage instanceof AttributesStorageOverBlobStorage newAttributesStorage)) {
      throw new UnsupportedOperationException("Raw attribute access is not implemented for " + attributesStorage.getClass().getName());
    }

    connection.ensureFileIdIsValid(fileId);
    throw new UnsupportedOperationException("Method not implemented yet");
    //TODO RC: drill hole for storage.writeAttributeRaw(connection, fileId, attribute, writer)
    //return storage.writeAttribute(connection, fileId, attribute, buffer -> {
    //  if (attribute.isVersioned()) {
    //    DataInputOutputUtil.writeINT(buffer);
    //  }
    //  return writer.write(buffer);
    //});
  }

  /**
   * Opens given attribute of given file for writing
   */
  public @NotNull AttributeOutputStream writeAttribute(final int fileId,
                                                       final @NotNull FileAttribute attribute) {
    connection.ensureFileIdIsValid(fileId);
    final AttributeOutputStream attributeStream = attributesStorage.writeAttribute(connection, fileId, attribute);
    if (attribute.isVersioned()) {
      try {
        DataInputOutputUtil.writeINT(attributeStream, attribute.getVersion());
      }
      catch (IOException e) {
        throw new AssertionError("Write to byte[]-backed stream should not throw IOException -- a bug?", e);
      }
    }

    return attributeStream;
  }

  public void deleteAttributes(final int fileId) throws IOException {
    connection.ensureFileIdIsValid(fileId);
    attributesStorage.deleteAttributes(connection, fileId);
  }
}
