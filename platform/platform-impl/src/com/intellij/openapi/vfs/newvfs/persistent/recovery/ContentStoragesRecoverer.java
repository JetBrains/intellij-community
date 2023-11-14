// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.*;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.storage.VFSContentStorage;
import com.intellij.util.io.storage.RecordIdIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
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
        checkContentStorage(loader);

        //If contentStorage is OK -> try to re-create contentHashesEnumerator, and re-fill it by contentsStorage data:
        tryRebuildHashesStorageByContentStorage(loader);

        //MAYBE IDEA-334517: re-check contentHashStorage: is it really 'fixed'?

        loader.problemsWereRecovered(contentStoragesProblems);
        LOG.info("ContentHashesEnumerator was successfully rebuild, ContentStorage was verified along the way");
      }
      catch (Throwable ex) {
        LOG.warn("ContentStorage check is failed: " + ex.getMessage());
        //Seems like the ContentStorage itself is broken -> clean both Content & ContentHashes storages,
        //  and invalidate all the contentId references from fs-records:

        VFSContentStorage contentStorage = loader.contentsStorage();
        contentStorage.closeAndClean();
        ContentHashEnumerator contentHashEnumerator = loader.contentHashesEnumerator();
        contentHashEnumerator.closeAndClean();

        VFSContentStorage emptyContentStorage = loader.createContentStorage(loader.contentsFile);
        ContentHashEnumerator emptyHashesEnumerator = PersistentFSLoader.createContentHashStorage(loader.contentsHashesFile);
        loader.setContentsStorage(emptyContentStorage);
        loader.setContentHashesEnumerator(emptyHashesEnumerator);


        cleanAllContentIds(records);
        // FIXME MAYBE VfsLog recovery related: we don't place a special event in VfsLog about content storage being cleared.
        //  Clearing all references to old contents from records should suffice.

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
        "Content enumerator recovery fails",
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
        try (var stream = contentStorage.readStream(contentId)) {
          stream.readAllBytes();
        }
        catch (Throwable t) {
          throw new IOException("file[#" + fileId + "].content[contentId: " + contentId + "] is broken, " + t, t);
        }
      }
    }
  }

  private static void tryRebuildHashesStorageByContentStorage(@NotNull PersistentFSLoader loader) throws IOException {

    ContentHashEnumerator contentHashEnumerator = loader.contentHashesEnumerator();
    contentHashEnumerator.closeAndClean();

    Path contentsHashesFile = loader.contentsHashesFile;
    ContentHashEnumerator recoveringHashesEnumerator = PersistentFSLoader.createContentHashStorage(contentsHashesFile);

    VFSContentStorage contentStorage = loader.contentsStorage();
    try {
      fillHashesEnumeratorByContentStorage(contentStorage, recoveringHashesEnumerator);
      loader.setContentHashesEnumerator(recoveringHashesEnumerator);
    }
    catch (Throwable t) {
      recoveringHashesEnumerator.closeAndClean();
      throw t;
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

  private static void fillHashesEnumeratorByContentStorage(@NotNull VFSContentStorage contentStorage,
                                                           @NotNull ContentHashEnumerator hashesEnumeratorToFill) throws IOException {
    //Try to fill hashesEnumerator from contentStorage records (and check contentIds match)
    // (along the way we also checks contentStorage is OK -- i.e. all the records could be read)
    for (RecordIdIterator it = contentStorage.createRecordIdIterator();
         it.hasNextId(); ) {
      int contentRecordId = it.nextId();
      try (var stream = contentStorage.readStream(contentRecordId)) {
        byte[] content = stream.readAllBytes();
        byte[] hash = PersistentFSContentAccessor.calculateHash(content, 0, content.length);
        int contentHashId = hashesEnumeratorToFill.enumerate(hash);
        if (contentHashId != contentRecordId) {
          throw new IOException(
            "Content enumerator recovery fails (content id: #" + contentRecordId + " hashed to different id: #" + contentHashId + ")"
          );
        }
      }
    }
  }
}
