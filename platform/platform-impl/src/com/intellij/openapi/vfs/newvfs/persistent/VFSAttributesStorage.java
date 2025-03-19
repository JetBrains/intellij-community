// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Adapter for different attributes storage implementations: adapts them to the same interface
 * used by {@link PersistentFSAttributeAccessor}.
 *
 * Threading: implementations are not obliged to be thread-safe -- calling code must ensure thread-safety, if needed
 */
@ApiStatus.Internal
public interface VFSAttributesStorage extends Forceable, Closeable {

  /**
   * Upper limit on the single attribute size.
   * I set a restrictive limit here because VFS file attributes are designed to be a small chunk of data attached to
   * a file, not blobs of 100s Kb -- such blobs could compromise the performance of VFS for nothing.
   * Implementations could have larger restrictions ({@linkplain AttributesStorageOverBlobStorage}: ~1Mb),
   * or even not have any restrictions at all -- but limit is stricter, to have some margins
   */
  int MAX_ATTRIBUTE_VALUE_SIZE = SystemProperties.getIntProperty("vfs.file-attribute-size-max", 800 * IOUtil.KiB);

  /** Log a warning if attribute value size is larger than that */
  int WARN_ATTRIBUTE_VALUE_SIZE = SystemProperties.getIntProperty("vfs.file-attribute-size-warn", 100 * IOUtil.KiB);

  /**
   * Limit the total number of attributes. To have too many different attributes is not a good idea, it's an
   * ab-use of the attribute API.
   * <p>
   * Current limit value is a bit arbitrary: the actual implementations limits' are wider (i.e. the
   * {@link AttributesStorageOverBlobStorage} binary format allows for 16k attributeId, see
   * {@link AttributesStorageOverBlobStorage#MAX_SUPPORTED_ATTRIBUTE_ID}) -- but stricter limit allows to have a reserve capacity.
   */
  int MAX_ATTRIBUTE_ID = SystemProperties.getIntProperty("vfs.file-attribute-max-id", 8 * 1024);

  /** Exclusive upper bound for inline attribute size: attribute is inlined if its size < this value */
  int INLINE_ATTRIBUTE_SMALLER_THAN = 64;

  int NON_EXISTENT_ATTRIBUTE_RECORD_ID = 0;

  int getVersion() throws IOException;

  void setVersion(int version) throws IOException;

  @Nullable AttributeInputStream readAttribute(@NotNull PersistentFSConnection connection,
                                               int fileId,
                                               @NotNull FileAttribute attribute) throws IOException;


  boolean hasAttributePage(@NotNull PersistentFSConnection connection,
                           int fileId,
                           @NotNull FileAttribute attribute) throws IOException;

  /**
   * Opens given attribute of given file for writing
   */
  @NotNull AttributeOutputStream writeAttribute(@NotNull PersistentFSConnection connection,
                                                int fileId,
                                                @NotNull FileAttribute attribute);

  void deleteAttributes(@NotNull PersistentFSConnection connection,
                        int fileId) throws IOException;

  boolean isEmpty() throws IOException;

  void checkAttributeRecordSanity(int fileId,
                                  int attributeRecordId) throws IOException;

  static void checkAttributeValueSize(@NotNull FileAttribute attribute,
                                      int attributeValueSize) throws FileTooBigException {
    if (attributeValueSize > MAX_ATTRIBUTE_VALUE_SIZE) {
      String message = "Attribute " + attribute + " value is too large: " +
                       attributeValueSize + " b > max(" + MAX_ATTRIBUTE_VALUE_SIZE + ")" +
                       " -> please, do not use VFS file attributes for huge blobs of data. Consider using GistManager or GistStorage.";
      //RC: exceptions from .close() methods are frequently ignored (see e.g. kryo/Output)
      //    => log the error right here, so ONE CAN'T SILENCE THE TRUTH!!!111
      FSRecords.LOG.error(message);
      throw new FileTooBigException(message);
    }
    else if (attributeValueSize > WARN_ATTRIBUTE_VALUE_SIZE) {
      FSRecords.THROTTLED_LOG.warn(
        "Attribute " + attribute + " value is quite large: " +
        attributeValueSize + " b > warn threshold(" + WARN_ATTRIBUTE_VALUE_SIZE + ")" +
        " -> please, do not use VFS file attributes for huge blobs of data. Consider using GistManager or GistStorage."
      );
    }
  }
}
