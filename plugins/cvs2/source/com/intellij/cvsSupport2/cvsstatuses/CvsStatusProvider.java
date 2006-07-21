package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsInfo;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.util.Collection;
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

  public static FileStatus getStatus(VirtualFile file) {
    if (!CvsEntriesManager.getInstance().isActive()) return FileStatus.NOT_CHANGED;
    return getStatus(file, getEntriesManager().getEntryFor(CvsVfsUtil.getParentFor(file), file.getName()));
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

  private static CvsEntriesManager getEntriesManager() {
    return CvsEntriesManager.getInstance();
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

  private static boolean isInContent(VirtualFile file, Module module) {
    return file == null || !FileTypeManager.getInstance().isFileIgnored(file.getName()) &&
                           ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
  }

  public static DirectoryContent getDirectoryContent(VirtualFile directory, Project project) {
    return getDirectoryContent(directory, VfsUtil.getModuleForFile(project, directory));
  }

  public static DirectoryContent getDirectoryContent(VirtualFile directory, Module module) {
    CvsInfo cvsInfo = getEntriesManager().getCvsInfoFor(directory);
    DirectoryContent result = new DirectoryContent(cvsInfo);

    VirtualFile[] children = CvsVfsUtil.getChildrenOf(directory);
    if (children == null) children = VirtualFile.EMPTY_ARRAY;

    Collection<Entry> entries = cvsInfo.getEntries();

    HashMap<String, VirtualFile> nameToFileMap = new HashMap<String, VirtualFile>();
    for (VirtualFile child : children) {
      nameToFileMap.put(child.getName(), child);
    }

    for (final Entry entry : entries) {
      String fileName = entry.getFileName();
      if (entry.isDirectory()) {
        if (nameToFileMap.containsKey(fileName)) {
          VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile, module)) {
            result.addDirectory(new VirtualFileEntry(virtualFile, entry));
          }
        }
        else if (!entry.isRemoved() && !FileTypeManager.getInstance().isFileIgnored(fileName)) {
          result.addDeletedDirectory(entry);          
        }
      }
      else {
        if (nameToFileMap.containsKey(fileName) || entry.isRemoved()) {
          VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile, module)) {
            result.addFile(new VirtualFileEntry(virtualFile, entry));
          }
        }
        else if (!entry.isAddedFile()) {
          result.addDeletedFile(entry);
        }
      }
      nameToFileMap.remove(fileName);
    }

    for (final String name : nameToFileMap.keySet()) {
      VirtualFile unknown = nameToFileMap.get(name);
      if (unknown.isDirectory()) {
        if (isInContent(unknown, module)) {
          result.addUnknownDirectory(unknown);
        }
      }
      else {
        if (isInContent(unknown, module)) {
          boolean isIgnored = result.getCvsInfo().getIgnoreFilter().shouldBeIgnored(unknown.getName());
          if (isIgnored) {
            result.addIgnoredFile(unknown);
          }
          else {
            result.addUnknownFile(unknown);
          }
        }
      }
    }

    return result;
  }

  public static Date createDateDiffersTo(long timeStamp) {
    return new Date(timeStamp - CvsStatusProvider.TIME_STAMP_EPSILON - 1);
  }

}
