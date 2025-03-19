// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSLoader;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage;
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException;
import com.intellij.util.io.storage.VFSContentStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.NULL_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.CONTENT_STORAGES_INCOMPLETE;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.CONTENT_STORAGES_NOT_MATCH;

/**
 * Knows how to recover CONTENT_STORAGES_XXX error categories:
 * <ol>
 * <li>if ContentStorage is not corrupted -> rebuild ContentHashes storage from it</li>
 * <li>if ContentStorage is corrupted -> clears both ContentStorage & ContentHashesStorage,
 *    and sets all .contentId references to NULL_ID. (This should invalidate local history
 *    later on)</li>
 * </ol>
 */
public final class ContentStoragesRecoverer implements VFSRecoverer {
  private static final Logger LOG = Logger.getInstance(ContentStoragesRecoverer.class);

  @Override
  public void tryRecover(@NotNull PersistentFSLoader loader) {
    List<VFSInitException> contentStoragesProblems = loader.problemsDuringLoad(
      CONTENT_STORAGES_NOT_MATCH,
      CONTENT_STORAGES_INCOMPLETE
    );
    if (contentStoragesProblems.isEmpty()) {
      return;
    }

    try {
      LOG.info(contentStoragesProblems.size() + " ContentStorage-related issue(s) -> trying to fix");
      PersistentFSRecordsStorage records = loader.recordsStorage();
      try {
        //Check: is there really any problem?
        checkContentStorage(loader);
        //if not -> false alarm? mark content-related problems as 'fixed'
        loader.problemsWereRecovered(contentStoragesProblems);
        LOG.warn("ContentStorage was verified, no problems found -> seems like false alarm?");
      }
      catch (Throwable ex) {
        LOG.warn("ContentStorage verification is failed: " + ex.getMessage());
        //Seems like the ContentStorage is totally broken -> clean everything,
        //  and invalidate all the contentId references from fs-records (and other places):

        VFSContentStorage contentStorage = loader.contentsStorage();
        contentStorage.closeAndClean();

        VFSContentStorage emptyContentStorage = loader.createContentStorage(loader.contentsHashesFile, loader.contentsFile);
        loader.setContentsStorage(emptyContentStorage);

        cleanAllContentIds(records);

        //inform others (LocalHistory) that old contentIds are no longer valid:
        loader.contentIdsInvalidated(true);
        loader.problemsWereRecovered(contentStoragesProblems);
        LOG.warn("ContentStorage is found broken -> fixed by invalidating all the content refs (LocalHistory is lost)");
      }
    }
    catch (Throwable t) {
      loader.problemsRecoveryFailed(
        contentStoragesProblems,
        CONTENT_STORAGES_INCOMPLETE,
        "Content storage recovery fails",
        t
      );
    }
  }

  private static void checkContentStorage(@NotNull PersistentFSLoader loader) throws IOException {
    PersistentFSRecordsStorage records = loader.recordsStorage();
    VFSContentStorage contentStorage = loader.contentsStorage();
    int maxAllocatedID = records.maxAllocatedID();
    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int contentId = records.getContentRecordId(fileId);
      if (contentId != NULL_ID) {
        try {
          contentStorage.checkRecord(contentId, /*fastCheck: */ false);
        }
        catch (Throwable t) {
          throw new IOException("file[#" + fileId + "].content[contentId: " + contentId + "] is broken, " + t, t);
        }
      }
    }
  }


  private static void cleanAllContentIds(@NotNull PersistentFSRecordsStorage records) throws IOException {
    int maxAllocatedID = records.maxAllocatedID();
    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      if (records.getContentRecordId(fileId) != NULL_ID) {
        records.setContentRecordId(fileId, NULL_ID);
      }
    }
  }
}
