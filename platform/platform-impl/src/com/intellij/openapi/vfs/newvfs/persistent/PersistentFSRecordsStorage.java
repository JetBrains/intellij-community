// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public interface PersistentFSRecordsStorage extends CleanableStorage {
  int NULL_ID = FSRecords.NULL_FILE_ID;
  int MIN_VALID_ID = NULL_ID + 1;

  /**
   * @return id of newly allocated record
   */
  int allocateRecord();

  void setAttributeRecordId(int fileId, int recordId) throws IOException;

  int getAttributeRecordId(int fileId) throws IOException;

  int getParent(int fileId) throws IOException;

  void setParent(int fileId, int parentId) throws IOException;

  int getNameId(int fileId) throws IOException;

  /** @return previous value of nameId */
  int setNameId(int fileId, int nameId) throws IOException;

  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setFlags(int fileId, int flags) throws IOException;

  //TODO RC: boolean updateFlags(fileId, int maskBits, boolean riseOrClean)

  long getLength(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setLength(int fileId, long length) throws IOException;

  long getTimestamp(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setTimestamp(int fileId, long timestamp) throws IOException;

  int getModCount(int fileId) throws IOException;

  //TODO RC: why we need this method? Record modification is detected by actual modification -- there
  //         are (seems to) no way to modify record bypassing it.
  //         We use the method to mark file record modified there something derived is modified -- e.g.
  //         children attribute or content. This looks suspicious to me: why we need to update _file_
  //         record version in those cases?
  void markRecordAsModified(int fileId) throws IOException;

  int getContentRecordId(int fileId) throws IOException;

  boolean setContentRecordId(int fileId, int recordId) throws IOException;

  @PersistentFS.Attributes int getFlags(int fileId) throws IOException;

  /**
   * Fills all record fields in one shot.
   * Fields modifications are not atomic: method should be used in absence of concurrent modification, e.g.
   * on startup, or for filling new, just allocated record, while fileId just allocated is not yet published
   * for other threads to access.
   */
  void fillRecord(int fileId,
                  long timestamp,
                  long length,
                  int flags,
                  int nameId,
                  int parentId,
                  boolean overwriteAttrRef) throws IOException;

  /**
   * @throws IndexOutOfBoundsException if fileId is outside of range (0..max] of the fileIds allocated so far
   */
  void cleanRecord(int fileId) throws IOException;

  /* ======================== STORAGE HEADER ============================================================================== */

  long getTimestamp() throws IOException;

  void setConnectionStatus(int code) throws IOException;

  int getConnectionStatus() throws IOException;

  int getErrorsAccumulated() throws IOException;

  void setErrorsAccumulated(int errors) throws IOException;

  void setVersion(int version) throws IOException;

  int getVersion() throws IOException;

  int getGlobalModCount();

  int recordsCount();

  /** @return max fileId already allocated by this storage */
  int maxAllocatedID();

  boolean isDirty();


  // TODO add a synchronization or requirement to be called on the loading
  @SuppressWarnings("UnusedReturnValue")
  boolean processAllRecords(@NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException;

  void force() throws IOException;

  void close() throws IOException;

  /** Close the storage and remove all its data files */
  @Override
  void closeAndClean() throws IOException;

  @FunctionalInterface
  interface FsRecordProcessor {
    void process(int fileId, int nameId, int flags, int parentId, int attributeRecordId, int contentId, boolean corrupted);
  }
}
