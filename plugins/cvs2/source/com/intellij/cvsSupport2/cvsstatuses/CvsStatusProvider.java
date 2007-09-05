package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.util.Date;

/**
 * author: lesya
 */
public class CvsStatusProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider");

  private static long TIME_STAMP_EPSILON = 3000;

  private CvsStatusProvider() {}

  public static void changeTimeStampEpsilonTo(long epsilon) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    TIME_STAMP_EPSILON = epsilon;
  }

  public static FileStatus getStatus(@NotNull VirtualFile file) {
    if (!CvsEntriesManager.getInstance().isActive()) return FileStatus.NOT_CHANGED;
    return getStatus(file, CvsEntriesManager.getInstance().getEntryFor(file.getParent(), file.getName()));
  }

  public static FileStatus getStatus(VirtualFile file, Entry entry) {
    if (file == null) {
      return getFileStatusForAbsentFile(entry);
    }

    if (entry == null) {
      return getFileStatusForAbsentEntry(file);
    }

    if (entry.isDirectory()) {
      return FileStatus.NOT_CHANGED;
    }

    if (entry.isAddedFile()) {
      return FileStatus.ADDED;
    }

    if (entry.isRemoved()) {
      return FileStatus.DELETED;
    }

    if (entry.isResultOfMerge()) {
      if (entry.isConflict()) {
        return FileStatus.MERGED_WITH_CONFLICTS;
      }
      else {
        return FileStatus.MERGE;
      }
    }

    Date revisionDate = entry.getLastModified();

    if (revisionDate == null) {
      return FileStatus.MODIFIED;
    }

    return (timeStampsAreEqual(revisionDate.getTime(), CvsVfsUtil.getTimeStamp(file))) ?
                                                                                       FileStatus.NOT_CHANGED : FileStatus.MODIFIED;

  }

  private static FileStatus getFileStatusForAbsentFile(Entry entry) {
    if (entry == null || entry.isDirectory()) {
      return FileStatus.UNKNOWN;
    }
    if (entry.isRemoved()) {
      return FileStatus.DELETED;
    }
    return FileStatus.DELETED_FROM_FS;
  }

  private static FileStatus getFileStatusForAbsentEntry(VirtualFile file) {
    if (file == null) {
      return FileStatus.UNKNOWN;
    }

    if (CvsEntriesManager.getInstance().fileIsIgnored(file)) {
      return FileStatus.IGNORED;
    }

    if (file.isDirectory() && CvsUtil.fileIsUnderCvs(file)) {
      return FileStatus.NOT_CHANGED;
    }

    return FileStatus.UNKNOWN;
  }

  public static boolean timeStampsAreEqual(long revisionTime, long fileTimestamp) {
    long diff = Math.abs(revisionTime - fileTimestamp);
    return isZero(diff) || isZero(Math.abs(diff - 3600000));
  }

  private static boolean isZero(long diff) {
    return diff < TIME_STAMP_EPSILON;
  }

  public static Date createDateDiffersTo(long timeStamp) {
    return new Date(timeStamp - CvsStatusProvider.TIME_STAMP_EPSILON - 1);
  }

}
