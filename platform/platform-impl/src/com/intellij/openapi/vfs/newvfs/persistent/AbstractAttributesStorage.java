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
public abstract class AbstractAttributesStorage implements Forceable, Closeable {

  /**
   * Exclusive upper bound for inline attribute size: attribute is inlined if its size < this value
   */
  public static final int INLINE_ATTRIBUTE_SMALLER_THAN = 64;

  public static final int NON_EXISTENT_ATTR_RECORD_ID = 0;

  protected AbstractAttributesStorage() { }


  public abstract int getVersion() throws IOException;

  public abstract void setVersion(final int version) throws IOException;

  @Nullable
  public abstract AttributeInputStream readAttribute(final PersistentFSConnection connection,
                                                     final int fileId,
                                                     final @NotNull FileAttribute attribute) throws IOException;


  public abstract boolean hasAttributePage(final PersistentFSConnection connection,
                                           final int fileId,
                                           final @NotNull FileAttribute attribute) throws IOException;

  /**
   * Opens given attribute of given file for writing
   */
  @NotNull
  public abstract AttributeOutputStream writeAttribute(final PersistentFSConnection connection,
                                                       final int fileId,
                                                       final @NotNull FileAttribute attribute);

  public abstract void deleteAttributes(final PersistentFSConnection connection,
                                        final int fileId) throws IOException;

  public abstract int getLocalModificationCount();

  public abstract void checkAttributesStorageSanity(final PersistentFSConnection connection,
                                                    final int fileId,
                                                    final @NotNull IntList usedAttributeRecordIds,
                                                    final @NotNull IntList validAttributeIds) throws IOException;
}
