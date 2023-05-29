// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Adapter for different attributes storage implementations: adapts them to the same interface
 * used by {@link PersistentFSAttributeAccessor}
 */
//TODO RC: rename to VFSAttributesStorage
public interface AbstractAttributesStorage extends Forceable, Closeable {

  /**
   * Upper limit on the single attribute size.
   * I set a restrictive limit here because VFS file attributes are designed to be a small chunk of data attached to
   * a file, not blobs of 100s Kb -- such blobs could compromise the performance of VFS for nothing.
   * Actual implementations could have larger restrictions ({@linkplain AttributesStorageOverBlobStorage}: ~1Mb),
   * or not have any restrictions ({@link AttributesStorageOld}).
   */
  int MAX_ATTRIBUTE_VALUE_SIZE = SystemProperties.getIntProperty("vfs.file-attribute-size-max", 800 * IOUtil.KiB);

  /** Log a warning if attribute value size is larger than that */
  int WARN_ATTRIBUTE_VALUE_SIZE = SystemProperties.getIntProperty("vfs.file-attribute-size-warn", 100 * IOUtil.KiB);

  /**
   * Exclusive upper bound for inline attribute size: attribute is inlined if its size < this value
   */
  int INLINE_ATTRIBUTE_SMALLER_THAN = 64;

  int NON_EXISTENT_ATTR_RECORD_ID = 0;

  int getVersion() throws IOException;

  void setVersion(final int version) throws IOException;

  @Nullable AttributeInputStream readAttribute(final @NotNull PersistentFSConnection connection,
                                               final int fileId,
                                               final @NotNull FileAttribute attribute) throws IOException;


  boolean hasAttributePage(final @NotNull PersistentFSConnection connection,
                           final int fileId,
                           final @NotNull FileAttribute attribute) throws IOException;

  /**
   * Opens given attribute of given file for writing
   */
  @NotNull AttributeOutputStream writeAttribute(final @NotNull PersistentFSConnection connection,
                                                final int fileId,
                                                final @NotNull FileAttribute attribute);

  void deleteAttributes(final @NotNull PersistentFSConnection connection,
                        final int fileId) throws IOException;

  int getLocalModificationCount();

  void checkAttributesStorageSanity(final @NotNull PersistentFSConnection connection,
                                    final int fileId,
                                    final @NotNull IntList usedAttributeRecordIds,
                                    final @NotNull IntList validAttributeIds) throws IOException;

  static void checkAttributeValueSize(@NotNull FileAttribute attribute,
                                      int attributeValueSize) throws FileTooBigException {
    if (attributeValueSize > MAX_ATTRIBUTE_VALUE_SIZE) {
      throw new FileTooBigException(
        "Attribute " + attribute + " value is too large: " +
        attributeValueSize + " b > max(" + MAX_ATTRIBUTE_VALUE_SIZE + ")" +
        " -> please, do not use VFS file attributes for huge blobs of data"
      );
    }
    else if (attributeValueSize > WARN_ATTRIBUTE_VALUE_SIZE) {
      FSRecords.LOG.warn(
        "Attribute " + attribute + " value is quite large: " +
        attributeValueSize + " b > warn threshold(" + WARN_ATTRIBUTE_VALUE_SIZE + ")" +
        " -> please, do not use VFS file attributes for huge blobs of data"
      );
    }
  }
}
