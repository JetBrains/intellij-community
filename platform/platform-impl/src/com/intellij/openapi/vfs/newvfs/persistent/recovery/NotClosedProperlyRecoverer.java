// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.*;
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.storage.VFSContentStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.FREE_RECORD_FLAG;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.*;

/**
 * Knows how to recover {@link ErrorCategory#NOT_CLOSED_PROPERLY} error category:
 * if VFS storages weren't closed properly -- they only sensible way to 'fix' it is to scan whole VFS
 * storages and check as many consistency invariants as possible.
 * This is what the class is doing: scans all file records, and checks the (name, attribute, content)
 * references are successfully resolved. If they do -- {@link ErrorCategory#NOT_CLOSED_PROPERLY}
 * is 'fixed', otherwise appropriate errors are escalated.
 */
public class NotClosedProperlyRecoverer implements VFSRecoverer {
  private static final Logger LOG = Logger.getInstance(NotClosedProperlyRecoverer.class);

  private static final int MAX_ERRORS_TO_REPORT = SystemProperties.getIntProperty("NotClosedProperlyRecoverer.MAX_ERRORS_TO_REPORT", 64);

  @Override
  public void tryRecover(@NotNull PersistentFSLoader loader) {
    List<VFSInitException> notClosedProperlyErrors = loader.problemsDuringLoad(NOT_CLOSED_PROPERLY);
    if (notClosedProperlyErrors.isEmpty()) {
      return;
    }

    LOG.info(notClosedProperlyErrors.size() + " not-closed-properly-related issue(s) -> trying to fix");

    PersistentFSRecordsStorage records = loader.recordsStorage();
    ScannableDataEnumeratorEx<String> namesEnumerator = loader.namesStorage();
    VFSContentStorage contentStorage = loader.contentsStorage();
    VFSAttributesStorage attributesStorage = loader.attributesStorage();
    try {
      int accumulatedErrors = records.getErrorsAccumulated();
      if (accumulatedErrors > 0) {
        //TODO RC: with more detailed recovery we could clear accumulatedErrors -- but current recovery procedures
        //         are not very strict, they could miss the errors easily, so I don't trust them too much.
        //         So if there were errors during the regular operations -> safer to fail the recovery, and rebuild VFS
        LOG.warn(accumulatedErrors + " errors accumulated in previous session -> " +
                 "stop the recovery because the chances to overlook crucial VFS errors are too big");
        loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                      HAS_ERRORS_IN_PREVIOUS_SESSION,
                                      accumulatedErrors + " errors accumulated in previous session -- too risky to recover");
      }

      int namesEnumeratorErrors = 0;
      int attributesStorageErrors = 0;
      int contentEnumeratorErrors = 0;
      int totalErrors = 0;
      int maxAllocatedID = records.maxAllocatedID();

      //Subset of VFSHealthChecker checks:
      for (int fileId = FSRecords.MIN_REGULAR_FILE_ID; fileId <= maxAllocatedID; fileId++) {
        int flags = records.getFlags(fileId);
        if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
          continue;
        }

        int parentId = records.getParent(fileId);
        int nameId = records.getNameId(fileId);
        int contentId = records.getContentRecordId(fileId);
        int attributeRecordId = records.getAttributeRecordId(fileId);

        if (nameId == DataEnumerator.NULL_ID) {
          LOG.warn("[fileId: #" + fileId + "]: nameId=NULL -> remove the file record and schedule file refresh");
          //remove file & schedule its invalidation, schedule parent for refresh:
          records.setFlags(fileId, flags | FREE_RECORD_FLAG);
          loader.postponeFileInvalidation(fileId);
          loader.postponeDirectoryRefresh(parentId);
        }
        else if (!nameResolvedSuccessfully(fileId, nameId, namesEnumerator)) {
          namesEnumeratorErrors++;
          totalErrors++;
        }

        if (!attributeRecordIsValid(fileId, attributeRecordId, attributesStorage)) {
          attributesStorageErrors++;
          totalErrors++;
        }

        if (contentId != DataEnumerator.NULL_ID) {
          if (!contentResolvedSuccessfully(fileId, contentId, contentStorage)) {
            contentEnumeratorErrors++;
            totalErrors++;
          }
        }//else: it is OK for contentId to be NULL

        if (totalErrors > MAX_ERRORS_TO_REPORT) {
          LOG.warn(totalErrors + " errors already detected -- no reason to continue, VFS needs rebuild anyway");
          break;
        }
      }

      if (namesEnumeratorErrors == 0 && attributesStorageErrors == 0 && contentEnumeratorErrors == 0) {
        LOG.info("No critical errors found -> VFS looks +/- healthy");
        loader.problemsWereRecovered(notClosedProperlyErrors);
        return;
      }

      if (namesEnumeratorErrors > 0) {
        loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                      NAME_STORAGE_INCOMPLETE,
                                      namesEnumerator + " nameIds are not resolved");
      }
      if (attributesStorageErrors > 0) {
        loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                      ATTRIBUTES_STORAGE_CORRUPTED,
                                      attributesStorageErrors + " attributeRecordIds are unreadable");
      }
      if (contentEnumeratorErrors > 0) {
        loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                      CONTENT_STORAGES_INCOMPLETE,
                                      contentEnumeratorErrors + " contentIds are not resolved");
      }
    }
    catch (Throwable t) {
      LOG.warn("Unexpected error during VFS consistency scan: " + t.getMessage());
      loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                    UNRECOGNIZED,
                                    "Unexpected error during VFS consistency scan", t);
    }
  }

  private static boolean attributeRecordIsValid(int fileId,
                                                int attributeRecordId,
                                                @NotNull VFSAttributesStorage attributesStorage) {
    try {
      attributesStorage.checkAttributeRecordSanity(fileId, attributeRecordId);
      return true;
    }
    catch (Throwable t) {
      LOG.warn("[fileId: #" + fileId + "]: failing to read attributes. " + t.getMessage());
      return false;
    }
  }

  private static boolean contentResolvedSuccessfully(int fileId,
                                                     int contentId,
                                                     @NotNull VFSContentStorage contentStorage) {
    try {
      contentStorage.checkRecord(contentId, /* fast: */ false);
    }
    catch (Throwable t) {
      LOG.warn("file[#" + fileId + "]: contentId(=" + contentId + ") content fails to resolve. " + t.getMessage());
      return false;
    }

    return true;
  }

  private static boolean nameResolvedSuccessfully(int fileId,
                                                  int nameId,
                                                  @NotNull ScannableDataEnumeratorEx<String> namesEnumerator) {
    try {
      String fileName = namesEnumerator.valueOf(nameId);
      if (fileName == null) {
        return false;
      }
      else {
        int nameIdResolvedBack = namesEnumerator.tryEnumerate(fileName);
        if (nameIdResolvedBack != nameId) {
          return false;
        }
      }
      return true;
    }
    catch (IOException e) {
      LOG.warn("file[#" + fileId + "]: nameId(=" + nameId + ") fails to resolve. " + e.getMessage());
      return false;
    }
  }
}
