// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.*;
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory;
import com.intellij.util.SystemProperties;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.storage.RefCountingContentStorage;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
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
  private final static Logger LOG = Logger.getInstance(NotClosedProperlyRecoverer.class);

  private static final int MAX_ERRORS_TO_REPORT = SystemProperties.getIntProperty("NotClosedProperlyRecoverer.MAX_ERRORS_TO_REPORT", 64);

  @Override
  public void tryRecover(@NotNull PersistentFSLoader loader) {
    List<VFSInitException> notClosedProperlyErrors = loader.problemsDuringLoad(NOT_CLOSED_PROPERLY);
    if (notClosedProperlyErrors.isEmpty()) {
      return;
    }

    PersistentFSRecordsStorage records = loader.recordsStorage();
    ScannableDataEnumeratorEx<String> namesEnumerator = loader.namesStorage();
    RefCountingContentStorage contentStorage = loader.contentsStorage();
    ContentHashEnumerator contentHashEnumerator = loader.contentHashesEnumerator();
    AbstractAttributesStorage attributesStorage = loader.attributesStorage();
    try {
      int accumulatedErrors = records.getErrorsAccumulated();
      if (accumulatedErrors > 0) {
        loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                      HAS_ERRORS_IN_PREVIOUS_SESSION,
                                      accumulatedErrors + " errors accumulated in previous session -- too dangerous to recover");
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
          if (!contentResolvedSuccessfully(fileId, contentId, contentStorage, contentHashEnumerator)) {
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
        loader.problemsWereRecovered(notClosedProperlyErrors);
        return;
      }

      //Fail-safe: if following recovery procedures fail to rebuild VFS -- on restart, we'll short-circuit
      // the detailing checks
      records.setErrorsAccumulated(accumulatedErrors + totalErrors);

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
      loader.problemsRecoveryFailed(notClosedProperlyErrors,
                                    UNRECOGNIZED,
                                    "Unexpected error during VFS consistency scan", t);
    }
  }

  private static boolean attributeRecordIsValid(int fileId,
                                                int attributeRecordId,
                                                @NotNull AbstractAttributesStorage attributesStorage) {
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
                                                     @NotNull RefCountingContentStorage contentStorage,
                                                     @NotNull ContentHashEnumerator contentHashEnumerator) {
    try (DataInputStream stream = contentStorage.readStream(contentId)) {
      stream.readAllBytes();
    }
    catch (IOException e) {
      LOG.warn("file[#" + fileId + "]: contentId(=" + contentId + ") content fails to resolve. " + e.getMessage());
      return false;
    }
    try {
      byte[] hash = contentHashEnumerator.valueOf(contentId);
      if (hash == null) {
        LOG.warn("file[#" + fileId + "]: contentId(=" + contentId + ") content hash fails to resolve (null)");
        return false;
      }
    }
    catch (IOException e) {
      LOG.warn("file[#" + fileId + "]: contentId(=" + contentId + ") content hash fails to resolve. " + e.getMessage());
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
