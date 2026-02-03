// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Forceable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * New style interface for access FSRecords: we need multi-threading-friendly interface now,
 * as we're moving away from global storage lock, towards fine-grained locking, or even lock-free
 * storage access. This interface allows implementation to decide how to protect specific record
 * access, without bothering clients with it.
 */
@ApiStatus.Internal
public interface IPersistentFSRecordsStorage extends Forceable, AutoCloseable {

  int recordsCount();

  /** Allows reader to read fields of the record[recordId], while holding appropriate locks */
  <R> R readRecord(int recordId,
                   @NotNull RecordReader<R> reader) throws IOException;

  /**
   * Allows updater to read and update fields of the record[recordId], while holding appropriate locks
   *
   * @return if recordId == -1 -> creates new record, and return its id, otherwise returns recordId passed in
   */
  int updateRecord(int recordId,
                   @NotNull RecordUpdater updater) throws IOException;

  <R> R readHeader(@NotNull HeaderReader<R> reader) throws IOException;

  void updateHeader(@NotNull HeaderUpdater updater) throws IOException;

  @FunctionalInterface
  interface RecordUpdater {
    /** @return true if actually modifies record (so it should be marked as modified), false otherwise */
    boolean updateRecord(@NotNull RecordForUpdate record) throws IOException;
  }

  @FunctionalInterface
  interface RecordReader<R> {
    R readRecord(@NotNull RecordForRead record) throws IOException;
  }

  @FunctionalInterface
  interface HeaderReader<R> {
    R readHeader(@NotNull HeaderForRead header) throws IOException;
  }

  @FunctionalInterface
  interface HeaderUpdater {
    boolean updateHeader(@NotNull HeaderForUpdate header) throws IOException;
  }

  interface RecordForRead {

    int recordId();

    int getAttributeRecordId() throws IOException;

    int getParent() throws IOException;

    int getNameId() throws IOException;

    long getLength() throws IOException;

    long getTimestamp() throws IOException;

    int getModCount() throws IOException;

    int getContentRecordId() throws IOException;

    @PersistentFS.Attributes int getFlags() throws IOException;
  }

  interface RecordForUpdate extends RecordForRead {
    void setAttributeRecordId(int recordId) throws IOException;

    void setParent(int parentId) throws IOException;

    void setNameId(int nameId) throws IOException;

    /** @return true if value is changed, false if not (i.e. new value is actually equal to the old one) */
    boolean setFlags(@PersistentFS.Attributes int flags) throws IOException;

    default boolean addFlags(@PersistentFS.Attributes int flagBits) throws IOException {
      int oldFlags = getFlags();
      int newFlags = oldFlags | flagBits;
      if (newFlags != oldFlags) {
        return setFlags(newFlags);
      }
      return false;
    }

    default boolean removeFlags(@PersistentFS.Attributes int flagBits) throws IOException {
      int oldFlags = getFlags();
      int newFlags = oldFlags & ~(flagBits);
      if (newFlags != oldFlags) {
        return setFlags(newFlags);
      }
      return false;
    }

    /** @return true if value is changed, false if not (i.e. new value is actually equal to the old one) */
    boolean setLength(long length) throws IOException;

    /** @return true if value is changed, false if not (i.e. new value is actually equal to the old one) */
    boolean setTimestamp(long timestamp) throws IOException;

    /** @return true if value is changed, false if not (i.e. new value is actually equal to the old one) */
    boolean setContentRecordId(int recordId) throws IOException;
  }

  interface HeaderForRead {
    long getTimestamp() throws IOException;

    //TODO boolean wasClosedProperly() throws IOException;

    int getVersion() throws IOException;

    int getGlobalModCount() throws IOException;

    //TODO int getErrorsAccumulated() throws IOException;
    //TODO byte getFlags() throws IOException;
  }

  interface HeaderForUpdate extends HeaderForRead {
    void setVersion(int version) throws IOException;

    //TODO void setErrorsAccumulated(int errors) throws IOException;
    //TODO void setFlags(byte flags) throws IOException;
  }
}
