// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

final class PersistentFSAttributeAccessor {

  @NotNull
  private final PersistentFSConnection connection;
  @NotNull
  private final AbstractAttributesStorage attributesStorage;

  PersistentFSAttributeAccessor(final @NotNull PersistentFSConnection connection) {
    this.connection = connection;
    attributesStorage = connection.getAttributes();
  }

  public boolean hasAttributePage(final int fileId,
                                  final @NotNull FileAttribute attribute) throws IOException {
    return attributesStorage.hasAttributePage(connection, fileId, attribute);
  }

  @Nullable
  public AttributeInputStream readAttribute(final int fileId,
                                            final @NotNull FileAttribute attribute) throws IOException {
    final AttributeInputStream attributeStream = attributesStorage.readAttribute(connection, fileId, attribute);
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

  /**
   * Opens given attribute of given file for writing
   */
  @NotNull
  public AttributeOutputStream writeAttribute(final int fileId,
                                              final @NotNull FileAttribute attribute) {
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
    attributesStorage.deleteAttributes(connection, fileId);
  }

  public int getLocalModificationCount() {
    return attributesStorage.getLocalModificationCount();
  }

  public void checkAttributesStorageSanity(final int fileId,
                                           final @NotNull IntList usedAttributeRecordIds,
                                           final @NotNull IntList validAttributeIds) throws IOException {
    attributesStorage.checkAttributesStorageSanity(connection, fileId, usedAttributeRecordIds, validAttributeIds);
  }
}
