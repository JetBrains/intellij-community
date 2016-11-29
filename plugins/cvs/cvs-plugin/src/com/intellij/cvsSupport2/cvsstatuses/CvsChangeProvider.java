/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsInfo;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.text.ParseException;
import java.util.*;

/**
 * @author max
 */
public class CvsChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider");

  private final CvsVcs2 myVcs;
  private final CvsEntriesManager myEntriesManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final ChangeListManager myChangeListManager;

  public CvsChangeProvider(final CvsVcs2 vcs, CvsEntriesManager entriesManager) {
    myVcs = vcs;
    myEntriesManager = entriesManager;
    myVcsManager = ProjectLevelVcsManager.getInstance(vcs.getProject());
    myChangeListManager = ChangeListManager.getInstance(vcs.getProject());
  }

  @Override
  public void getChanges(@NotNull final VcsDirtyScope dirtyScope, @NotNull final ChangelistBuilder builder, @NotNull final ProgressIndicator progress,
                         @NotNull final ChangeListManagerGate addGate) throws VcsException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing changes for scope " + dirtyScope);
    }
    final HashSet<VirtualFile> cvsRoots = ContainerUtil.newHashSet(myVcsManager.getRootsUnderVcs(myVcs));
    showBranchImOn(builder, dirtyScope, cvsRoots);

    for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
      final VirtualFile dir = path.getVirtualFile();
      if (dir != null) {
        processEntriesIn(dir, dirtyScope, builder, true, cvsRoots, progress);
      }
      else {
        processFile(path, builder, progress);
      }
    }

    for (FilePath path : dirtyScope.getDirtyFiles()) {
      if (path.isDirectory()) {
        final VirtualFile dir = path.getVirtualFile();
        if (dir != null) {
          processEntriesIn(dir, dirtyScope, builder, false, cvsRoots, progress);
        }
        else {
          processFile(path, builder, progress);
        }
      }
      else {
        processFile(path, builder, progress);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Done processing changes");
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  @Override
  public void doCleanup(final List<VirtualFile> files) {}

  private void processEntriesIn(@NotNull VirtualFile dir, VcsDirtyScope scope, ChangelistBuilder builder, boolean recursively,
                                Collection<VirtualFile> cvsRoots, final ProgressIndicator progress) throws VcsException {
    final FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePathOn(dir);
    if (!scope.belongsTo(path)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Skipping out of scope path " + path);
      }
      return;
    }
    final DirectoryContent dirContent = getDirectoryContent(dir, progress);

    for (VirtualFile file : dirContent.getUnknownFiles()) {
      builder.processUnversionedFile(file);
    }
    for (VirtualFile file : dirContent.getIgnoredFiles()) {
      builder.processIgnoredFile(file);
    }

    for (Entry entry : dirContent.getDeletedDirectories()) {
      builder.processLocallyDeletedFile(VcsUtil.getFilePath(CvsVfsUtil.getFileFor(dir, entry.getFileName()), true));
    }

    for (Entry entry : dirContent.getDeletedFiles()) {
      builder.processLocallyDeletedFile(VcsUtil.getFilePath(CvsVfsUtil.getFileFor(dir, entry.getFileName()), false));
    }

    /*
    final Collection<VirtualFile> unknownDirs = dirContent.getUnknownDirectories();
    for (VirtualFile file : unknownDirs) {
      builder.processUnversionedFile(file);
    }
    */

    progress.checkCanceled();
    checkSwitchedDir(dir, builder, scope, cvsRoots);

    if (CvsUtil.fileIsUnderCvs(dir) && dir.getChildren().length == 1 /* admin dir */ &&
        dirContent.getDeletedFiles().isEmpty() && hasRemovedFiles(dirContent.getFiles())) {
      // directory is going to be deleted
      builder.processChange(new Change(CurrentContentRevision.create(path), CurrentContentRevision.create(path), FileStatus.DELETED), CvsVcs2.getKey());
    }
    for (VirtualFileEntry fileEntry : dirContent.getFiles()) {
      processFile(dir, fileEntry.getVirtualFile(), fileEntry.getEntry(), builder, progress);
    }

    if (recursively) {
      for (VirtualFile file : CvsVfsUtil.getChildrenOf(dir)) {
        progress.checkCanceled();
        if (file.isDirectory()) {
          if (!myVcsManager.isIgnored(file)) {
            processEntriesIn(file, scope, builder, true, cvsRoots, progress);
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Skipping ignored path " + file.getPath());
            }
          }
        }
      }
    }
  }

  private static boolean hasRemovedFiles(final Collection<VirtualFileEntry> files) {
    for(VirtualFileEntry e: files) {
      if (e.getEntry().isRemoved()) {
        return true;
      }
    }
    return false;
  }

  private void processFile(final FilePath filePath, final ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException {
    final VirtualFile dir = filePath.getVirtualFileParent();
    if (dir == null) return;

    final Entry entry = myEntriesManager.getEntryFor(dir, filePath.getName());
    final FileStatus status = CvsStatusProvider.getStatus(filePath.getVirtualFile(), entry);
    final VcsRevisionNumber number = entry != null ? createRevisionNumber(entry.getRevision(), status) : VcsRevisionNumber.NULL;
    processStatus(filePath, dir.findChild(filePath.getName()), status, number, builder);
    progress.checkCanceled();
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder,
                           final ProgressIndicator progress) throws VcsException {
    final FilePath filePath = VcsUtil.getFilePath(dir, entry.getFileName());
    final FileStatus status = CvsStatusProvider.getStatus(file, entry);
    final VcsRevisionNumber number = createRevisionNumber(entry.getRevision(), status);
    processStatus(filePath, file, status, number, builder);
    progress.checkCanceled();
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private static CvsRevisionNumber createRevisionNumber(final String revision, final FileStatus status) {
    final String correctedRevision;
    if (FileStatus.DELETED.equals(status)) {
      final int idx = revision.indexOf('-');
      correctedRevision = (idx != -1) ? revision.substring(idx + 1) : revision;
    } else {
      correctedRevision = revision;
    }
    return new CvsRevisionNumber(correctedRevision);
  }

  private void showBranchImOn(final ChangelistBuilder builder, final VcsDirtyScope scope, HashSet<VirtualFile> cvsRoots) {
    final List<VirtualFile> dirs = ObjectsConvertor.fp2vf(scope.getRecursivelyDirtyDirectories());
    for (VirtualFile root : cvsRoots) {
      if (dirs.contains(root)) {
        checkTopLevelForBeingSwitched(root, builder);
      }
    }
  }

  private void checkTopLevelForBeingSwitched(final VirtualFile dir, final ChangelistBuilder builder) {
    final CvsInfo info = myEntriesManager.getCvsInfoFor(dir);
    if (info.getRepository() == null) return;
    final String dirTag = info.getStickyTag();
    if (dirTag != null) {
      final String caption = getSwitchedTagCaption(dirTag, null, false);
      if (caption != null) {
        builder.processRootSwitch(dir, caption);
      }
    } else {
      builder.processRootSwitch(dir, CvsUtil.HEAD);
    }
  }

  @Nullable
  private static String getSwitchedTagCaption(final String tag, @Nullable final String parentTag, final boolean checkParentTag) {
    if (tag == null) return CvsUtil.HEAD;
    final String tagOnly = tag.substring(1);
    if (CvsUtil.isNonDateTag(tag)) {
      // a switch between a branch tag and a non-branch tag is not a switch
      if (checkParentTag && parentTag != null && CvsUtil.isNonDateTag(parentTag)) {
        final String parentTagOnly = parentTag.substring(1);
        if (tagOnly.equals(parentTagOnly)) {
          return null;
        }
      }
      return CvsBundle.message("switched.tag.format", tagOnly);
    }
    else if (tag.startsWith(CvsUtil.STICKY_DATE_PREFIX)) {
      try {
        final Date date = Entry.STICKY_DATE_FORMAT.parse(tagOnly);
        return CvsBundle.message("switched.date.format", date);
      }
      catch (ParseException e) {
        return CvsBundle.message("switched.date.format", tagOnly);
      }
    }
    return null;
  }

  private void checkSwitchedDir(final VirtualFile dir,
                                final ChangelistBuilder builder,
                                final VcsDirtyScope scope,
                                Collection<VirtualFile> cvsRoots) {
    final VirtualFile parentDir = dir.getParent();
    if (parentDir == null || cvsRoots.contains(dir) || !myVcsManager.isFileInContent(parentDir)) {
      return;
    }
    final CvsInfo info = myEntriesManager.getCvsInfoFor(dir);
    if (info.getRepository() == null) {
      if (info.getIgnoreFilter().shouldBeIgnored(dir)) {
        builder.processIgnoredFile(dir);
      }
      else {
        builder.processUnversionedFile(dir);
      }
      return;
    }
    final String dirTag = info.getStickyTag();
    final CvsInfo parentInfo = myEntriesManager.getCvsInfoFor(parentDir);
    final String parentDirTag = parentInfo.getStickyTag();
    if (!Comparing.equal(dirTag, parentDirTag)) {
      final String caption = getSwitchedTagCaption(dirTag, parentDirTag, true);
      if (caption != null) {
        builder.processSwitchedFile(dir, caption, true);
      }
    }
    else if (!scope.belongsTo(VcsContextFactory.SERVICE.getInstance().createFilePathOn(parentDir))) {
      // check if we're doing a partial refresh below a switched dir (IDEADEV-16611)
      final String parentBranch = myChangeListManager.getSwitchedBranch(parentDir);
      if (parentBranch != null) {
        builder.processSwitchedFile(dir, parentBranch, true);
      }
    }
  }

  private void checkSwitchedFile(final FilePath filePath, final ChangelistBuilder builder, final VirtualFile dir, final Entry entry) {
    // if content root itself is switched, ignore
    if (!myVcsManager.isFileInContent(dir)) {
      return;
    }
    final String dirTag = myEntriesManager.getCvsInfoFor(dir).getStickyTag();
    final String dirStickyInfo = getStickyInfo(dirTag);
    if (entry != null && !Comparing.equal(entry.getStickyInformation(), dirStickyInfo)) {
      final VirtualFile file = filePath.getVirtualFile();
      if (file != null) {
        if (entry.getStickyTag() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.tag.format", entry.getStickyTag()), false);
        }
        else if (entry.getStickyDate() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.date.format", entry.getStickyDate()), false);
        }
        else if (entry.getStickyRevision() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.revision.format", entry.getStickyRevision()), false);
        }
        else {
          builder.processSwitchedFile(file, CvsUtil.HEAD, false);
        }
      }
    }
  }

  @Nullable
  private static String getStickyInfo(final String dirTag) {
    return (dirTag != null && dirTag.length() > 1) ? dirTag.substring(1) : null;
  }

  private void processStatus(final FilePath filePath,
                             final VirtualFile file,
                             final FileStatus status,
                             final VcsRevisionNumber number,
                             final ChangelistBuilder builder) throws VcsException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("processStatus: filePath=" + filePath + " status=" + status);
    }
    if (status == FileStatus.NOT_CHANGED) {
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        builder.processChange(
          new Change(createCvsRevision(filePath, number), CurrentContentRevision.create(filePath), FileStatus.MODIFIED), CvsVcs2.getKey());
      }
      return;
    }
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      final CvsUpToDateRevision beforeRevision = createCvsRevision(filePath, number);
      final ContentRevision afterRevision = CurrentContentRevision.create(filePath);
      if (beforeRevision instanceof BinaryContentRevision) {
        final byte[] binaryContent = ((BinaryContentRevision)beforeRevision).getBinaryContent();
        if (binaryContent != null && Arrays.equals(binaryContent, ((BinaryContentRevision)afterRevision).getBinaryContent())) {
          return;
        }
      }
      else {
        final String content = beforeRevision.getContent();
        if (content != null && content.equals(afterRevision.getContent())) {
          return;
        }
      }
      builder.processChange(new Change(beforeRevision, afterRevision, status), CvsVcs2.getKey());
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, CurrentContentRevision.create(filePath), status), CvsVcs2.getKey());
    }
    else if (status == FileStatus.DELETED) {
      // not sure about deleted content
      builder.processChange(new Change(createCvsRevision(filePath, number), null, status), CvsVcs2.getKey());
    }
    else if (status == FileStatus.DELETED_FROM_FS) {
      builder.processLocallyDeletedFile(filePath);
    }
    else if (status == FileStatus.UNKNOWN) {
      builder.processUnversionedFile(filePath.getVirtualFile());
    }
    else if (status == FileStatus.IGNORED) {
      builder.processIgnoredFile(filePath.getVirtualFile());
    }
  }

  @Nullable
  public byte[] getLastUpToDateContentFor(@NotNull final VirtualFile f) {
    final VirtualFile parent = f.getParent();
    final String name = f.getName();
    final Entry entry = myEntriesManager.getEntryFor(parent, name);
    if (entry != null && entry.isResultOfMerge()) {
      // try created by VCS during merge
      final byte[] content = CvsUtil.getStoredContentForFile(f, entry.getRevision());
      if (content != null) {
        return content;
      }
      // try cached by IDEA in CVS dir
      return CvsUtil.getCachedStoredContent(parent, name, entry.getRevision());
    }
    final long upToDateTimestamp = getUpToDateTimeForFile(f);
    final FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return CvsStatusProvider.timeStampsAreEqual(upToDateTimestamp, revisionTimestamp);
      }
    };
    final byte[] localHistoryContent = LocalHistory.getInstance().getByteContent(f, c);
    if (localHistoryContent == null) {
      if (entry != null && CvsUtil.haveCachedContent(f, entry.getRevision())) {
        return CvsUtil.getCachedStoredContent(parent, name, entry.getRevision());
      }
    }
    return localHistoryContent;
  }

  public long getUpToDateTimeForFile(@NotNull VirtualFile vFile) {
    final Entry entry = myEntriesManager.getEntryFor(vFile.getParent(), vFile.getName());
    if (entry == null) return -1;
    // retrieve of any file version in time is not correct since update/merge was applie3d to already modified file
    /*if (entry.isResultOfMerge()) {
      long resultForMerge = CvsUtil.getUpToDateDateForFile(vFile);
      if (resultForMerge > 0) {
        return resultForMerge;
      }
    }*/

    final Date lastModified = entry.getLastModified();
    if (lastModified == null) return -1;
    return lastModified.getTime();
  }

  private CvsUpToDateRevision createCvsRevision(FilePath filePath, VcsRevisionNumber revisionNumber) {
    if (filePath.getFileType().isBinary()) {
      return new CvsUpToDateBinaryRevision(filePath, revisionNumber);
    }
    return new CvsUpToDateRevision(filePath, revisionNumber);
  }

  private static boolean isInContent(VirtualFile file) {
    return file == null || !FileTypeManager.getInstance().isFileIgnored(file);
  }

  private static DirectoryContent getDirectoryContent(VirtualFile directory, final ProgressIndicator progress) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Retrieving directory content for " + directory);
    }
    final CvsInfo cvsInfo = CvsEntriesManager.getInstance().getCvsInfoFor(directory);
    final DirectoryContent result = new DirectoryContent(cvsInfo);

    final HashMap<String, VirtualFile> nameToFileMap = new HashMap<>();
    for (VirtualFile child : CvsVfsUtil.getChildrenOf(directory)) {
      nameToFileMap.put(child.getName(), child);
    }

    for (final Entry entry : cvsInfo.getEntries()) {
      progress.checkCanceled();
      final String fileName = entry.getFileName();
      if (entry.isDirectory()) {
        if (nameToFileMap.containsKey(fileName)) {
          final VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile)) {
            result.addDirectory(new VirtualFileEntry(virtualFile, entry));
          }
        }
        else if (!entry.isRemoved() && !FileTypeManager.getInstance().isFileIgnored(fileName)) {
          result.addDeletedDirectory(entry);
        }
      }
      else {
        if (nameToFileMap.containsKey(fileName) || entry.isRemoved()) {
          final VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile)) {
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
      progress.checkCanceled();
      final VirtualFile unknown = nameToFileMap.get(name);
      if (unknown.isDirectory()) {
        if (isInContent(unknown)) {
          result.addUnknownDirectory(unknown);
        }
      }
      else {
        if (isInContent(unknown)) {
          final boolean isIgnored = result.getCvsInfo().getIgnoreFilter().shouldBeIgnored(unknown);
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

  private class CvsUpToDateRevision implements ByteBackedContentRevision {
    protected final FilePath myPath;
    private final VcsRevisionNumber myRevisionNumber;

    private byte[] myContent;

    protected CvsUpToDateRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      myRevisionNumber = revisionNumber;
      myPath = path;
    }

    @Override
    @Nullable
    public String getContent() throws VcsException {
      final byte[] fileBytes = getContentAsBytes();
      return fileBytes == null ? null : CharsetToolkit.bytesToString(fileBytes, myPath.getCharset());
    }

    @Nullable
    @Override
    public byte[] getContentAsBytes() throws VcsException {
      if (myContent == null) {
        try {
          myContent = getUpToDateBinaryContent();
        }
        catch (CannotFindCvsRootException e) {
          throw new VcsException(e);
        }
      }
      return myContent;
    }

    @Nullable
    private byte[] getUpToDateBinaryContent() throws CannotFindCvsRootException {
      final VirtualFile virtualFile = myPath.getVirtualFile();
      byte[] result = null;
      if (virtualFile != null) {
        result = getLastUpToDateContentFor(virtualFile);
      }
      if (result == null) {
        String revision = null;
        final GetFileContentOperation operation;
        if (virtualFile != null) {
          // todo maybe refactor where data lives
          final Entry entry = myEntriesManager.getEntryFor(virtualFile.getParent(), virtualFile.getName());
          if (entry != null) {
            revision = entry.getRevision();
            operation = GetFileContentOperation.createForFile(virtualFile, new SimpleRevision(revision));
          } else {
            operation = GetFileContentOperation.createForFile(myPath);
          }
        }
        else {
          operation = GetFileContentOperation.createForFile(myPath);
        }
        if (operation.getRoot().isOffline()) return null;
        CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
        result = operation.tryGetFileBytes();

        if (result != null && revision != null) {
          // cache in CVS area to reduce remote requests number (old revisions are deleted)
          CvsUtil.storeContentForRevision(virtualFile, revision, result);
        }
      }
      return result;
    }

    @Override
    @NotNull
    public FilePath getFile() {
      return myPath;
    }

    @Override
    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevisionNumber;
    }

    @NonNls
    public String toString() {
      return "CvsUpToDateRevision:" + myPath; 
    }
  }

  private class CvsUpToDateBinaryRevision extends CvsUpToDateRevision implements BinaryContentRevision {
    public CvsUpToDateBinaryRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      super(path, revisionNumber);
    }

    @Override
    @Nullable
    public byte[] getBinaryContent() throws VcsException {
      return getContentAsBytes();
    }

    @NonNls
    public String toString() {
      return "CvsUpToDateBinaryRevision:" + myPath;
    }
  }
}
