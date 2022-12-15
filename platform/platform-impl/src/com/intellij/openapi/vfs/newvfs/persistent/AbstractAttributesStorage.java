// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Adapter for different attributes storage implementations: adapts them to the same interface
 * used by {@link PersistentFSAttributeAccessor}
 */
public interface AbstractAttributesStorage extends Forceable, Closeable {

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
}
