/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.changeBrowser.CvsBinaryContentRevision;
import com.intellij.cvsSupport2.changeBrowser.CvsContentRevision;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.*;

/**
 * @author max
 */
public class CvsChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider");

  private final CvsVcs2 myVcs;
  private final CvsEntriesManager myEntriesManager;
  private final ProjectFileIndex myFileIndex;
  private final ChangeListManager myChangeListManager;

  public CvsChangeProvider(final CvsVcs2 vcs, CvsEntriesManager entriesManager) {
    myVcs = vcs;
    myEntriesManager = entriesManager;
    myFileIndex = ProjectRootManager.getInstance(vcs.getProject()).getFileIndex();
    myChangeListManager = ChangeListManager.getInstance(vcs.getProject());
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress,
                         final ChangeListManagerGate addGate) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing changes for scope " + dirtyScope);
    }
    final Runnable checkCanceled = new Runnable() {
      public void run() {
        if (progress != null) {
          progress.checkCanceled();
        }
      }
    };

    showBranchImOn(builder, dirtyScope);

    for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
      final VirtualFile dir = path.getVirtualFile();
      checkCanceled.run();
      if (dir != null) {
        processEntriesIn(dir, dirtyScope, builder, true, checkCanceled);
      }
      else {
        processFile(path, builder, checkCanceled);
      }
    }

    for (FilePath path : dirtyScope.getDirtyFiles()) {
      checkCanceled.run();
      if (path.isDirectory()) {
        final VirtualFile dir = path.getVirtualFile();
        if (dir != null) {
          processEntriesIn(dir, dirtyScope, builder, false, checkCanceled);
        }
        else {
          processFile(path, builder, checkCanceled);
        }
      }
      else {
        processFile(path, builder, checkCanceled);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Done processing changes");
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(final List<VirtualFile> files) {
  }

  private void processEntriesIn(@NotNull VirtualFile dir, VcsDirtyScope scope, ChangelistBuilder builder, boolean recursively,
                                final Runnable checkCanceled) {
    final FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePathOn(dir);
    if (!scope.belongsTo(path)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Skipping out of scope path " + path);
      }
      return;
    }
    final DirectoryContent dirContent = getDirectoryContent(dir, checkCanceled);

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

    checkSwitchedDir(dir, builder, scope);

    if (CvsUtil.fileIsUnderCvs(dir) && dir.getChildren().length == 1 /* admin dir */ &&
        dirContent.getDeletedFiles().isEmpty() && hasRemovedFiles(dirContent.getFiles())) {
      // directory is going to be deleted
      builder.processChange(new Change(CurrentContentRevision.create(path), CurrentContentRevision.create(path), FileStatus.DELETED), CvsVcs2.getKey());
    }
    for (VirtualFileEntry fileEntry : dirContent.getFiles()) {
      processFile(dir, fileEntry.getVirtualFile(), fileEntry.getEntry(), builder, checkCanceled);
    }

    if (recursively) {
      final VirtualFile[] children = CvsVfsUtil.getChildrenOf(dir);
      if (children != null) {
        for (VirtualFile file : children) {
          if (file.isDirectory()) {
            final boolean isIgnored = myFileIndex.isIgnored(file);
            if (!isIgnored) {
              processEntriesIn(file, scope, builder, true, checkCanceled);
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
  }

  private static boolean hasRemovedFiles(final Collection<VirtualFileEntry> files) {
    for(VirtualFileEntry e: files) {
      if (e.getEntry().isRemoved()) {
        return true;
      }
    }
    return false;
  }


  private void processFile(final FilePath filePath, final ChangelistBuilder builder, final Runnable checkCanceled) {
    checkCanceled.run();
    final VirtualFile dir = filePath.getVirtualFileParent();
    if (dir == null) return;

    final Entry entry = myEntriesManager.getEntryFor(dir, filePath.getName());
    final FileStatus status = CvsStatusProvider.getStatus(filePath.getVirtualFile(), entry);
    VcsRevisionNumber number = entry != null ? createRevisionNumber(entry.getRevision(), status) : VcsRevisionNumber.NULL;
    processStatus(filePath, dir.findChild(filePath.getName()), status, number, entry != null && entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder,
                           final Runnable checkCanceled) {
    checkCanceled.run();
    final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(dir, entry.getFileName());
    final FileStatus status = CvsStatusProvider.getStatus(file, entry);
    final VcsRevisionNumber number = createRevisionNumber(entry.getRevision(), status);
    processStatus(filePath, file, status, number, entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private CvsRevisionNumber createRevisionNumber(final String revision, final FileStatus status) {
    final String correctedRevision;
    if (FileStatus.DELETED.equals(status)) {
      final int idx = revision.indexOf('-');
      correctedRevision = (idx != -1) ? revision.substring(idx + 1) : revision;
    } else {
      correctedRevision = revision;
    }
    return new CvsRevisionNumber(correctedRevision);
  }

  private void showBranchImOn(final ChangelistBuilder builder, final VcsDirtyScope scope) {
    final List<VirtualFile> dirs = ObjectsConvertor.fp2vf(scope.getRecursivelyDirtyDirectories());
    final Collection<VirtualFile> roots = new ArrayList<VirtualFile>(scope.getAffectedContentRoots());

    for (Iterator<VirtualFile> iterator = roots.iterator(); iterator.hasNext();) {
      final VirtualFile root = iterator.next();
      if (! dirs.contains(root)) iterator.remove();
    }

    if (roots.isEmpty()) return;
    for (VirtualFile root : roots) {
      checkTopLevelForBeingSwitched(root, builder);
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
  private static String getSwitchedTagCaption(final String tag, final String parentTag, final boolean checkParentTag) {
    if (tag == null) return CvsUtil.HEAD;
    final String tagOnly = tag.substring(1);
    if (CvsUtil.isNonDateTag(tag)) {
      // a switch between a branch tag and a non-branch tag is not a switch
      if (checkParentTag && parentTag != null && CvsUtil.isNonDateTag(parentTag)) {
        String parentTagOnly = parentTag.substring(1);
        if (tagOnly.equals(parentTagOnly)) {
          return null;
        }
      }
      return CvsBundle.message("switched.tag.format", tagOnly);
    }
    else if (tag.startsWith(CvsUtil.STICKY_DATE_PREFIX)) {
      try {
        Date date = Entry.STICKY_DATE_FORMAT.parse(tagOnly);
        return CvsBundle.message("switched.date.format", date);
      }
      catch (ParseException e) {
        return CvsBundle.message("switched.date.format", tagOnly);
      }
    }
    return null;
  }

  private void checkSwitchedDir(final VirtualFile dir, final ChangelistBuilder builder, final VcsDirtyScope scope) {
    VirtualFile parentDir = dir.getParent();
    if (parentDir == null || !myFileIndex.isInContent(parentDir)) {
      return;
    }
    final CvsInfo info = myEntriesManager.getCvsInfoFor(dir);
    if (info.getRepository() == null) {
      // don't report unversioned directories as switched (IDEADEV-17178)
      builder.processUnversionedFile(dir);
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
    if (!myFileIndex.isInContent(dir)) {
      return;
    }
    final String dirTag = myEntriesManager.getCvsInfoFor(dir).getStickyTag();
    String dirStickyInfo = getStickyInfo(dirTag);
    if (entry != null && !Comparing.equal(entry.getStickyInformation(), dirStickyInfo)) {
      VirtualFile file = filePath.getVirtualFile();
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
                             final boolean isBinary,
                             final ChangelistBuilder builder) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("processStatus: filePath=" + filePath + " status=" + status);
    }
    if (status == FileStatus.NOT_CHANGED) {
      if (file != null && FileDocumentManager.getInstance().isFileModifiedAndDocumentUnsaved(file)) {
        builder.processChange(
          new Change(createCvsRevision(filePath, number, isBinary), CurrentContentRevision.create(filePath), FileStatus.MODIFIED), CvsVcs2.getKey());
      }
      return;
    }
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary),
          CurrentContentRevision.create(filePath), status), CvsVcs2.getKey());
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, CurrentContentRevision.create(filePath), status), CvsVcs2.getKey());
    }
    else if (status == FileStatus.DELETED) {
      // not sure about deleted content
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary), null, status), CvsVcs2.getKey());
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

  private ContentRevision createRemote(final CvsRevisionNumber revisionNumber, final VirtualFile selectedFile) {
    final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(selectedFile.getParent());
    final File file = new File(CvsUtil.getModuleName(selectedFile));

    final RevisionOrDate versionInfo;
    if (revisionNumber.getDateOrRevision() != null) {
      versionInfo = RevisionOrDateImpl.createOn(revisionNumber.getDateOrRevision());
    }
    else {
      versionInfo = new SimpleRevision(revisionNumber.asString());
    }

    final Project project = myVcs.getProject();
    final File ioFile = new File(selectedFile.getPath());
    if (selectedFile.getFileType().isBinary()) {
      return new CvsBinaryContentRevision(file, ioFile, versionInfo, settings, project);
    }
    else {
      return new CvsContentRevision(file, ioFile, versionInfo, settings, project);
    }
  }

  @Nullable
  public byte[] getLastUpToDateContentFor(@NotNull final VirtualFile f) {
    Entry entry = myEntriesManager.getEntryFor(f.getParent(), f.getName());
    if (entry != null && entry.isResultOfMerge()) {
      // try created by VCS during merge
      byte[] content = CvsUtil.getStoredContentForFile(f, entry.getRevision());
      if (content != null) {
        return content;
      }
      // try cached by IDEA in CVS dir
      return CvsUtil.getCachedStoredContent(f, entry.getRevision());
    }
    final long upToDateTimestamp = getUpToDateTimeForFile(f);
    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return CvsStatusProvider.timeStampsAreEqual(upToDateTimestamp, revisionTimestamp);
      }
    };
    byte[] localHistoryContent = LocalHistory.getByteContent(myVcs.getProject(), f, c);
    if (localHistoryContent == null) {
      if (entry != null && CvsUtil.haveCachedContent(f, entry.getRevision())) {
        return CvsUtil.getCachedStoredContent(f, entry.getRevision());
      }
    }
    return localHistoryContent;
  }

  public long getUpToDateTimeForFile(@NotNull VirtualFile vFile) {
    Entry entry = myEntriesManager.getEntryFor(vFile.getParent(), vFile.getName());
    if (entry == null) return -1;
    // retrieve of any file version in time is not correct since update/merge was applie3d to already modified file
    /*if (entry.isResultOfMerge()) {
      long resultForMerge = CvsUtil.getUpToDateDateForFile(vFile);
      if (resultForMerge > 0) {
        return resultForMerge;
      }
    }*/

    Date lastModified = entry.getLastModified();
    if (lastModified == null) return -1;
    return lastModified.getTime();
  }

  private CvsUpToDateRevision createCvsRevision(FilePath path, VcsRevisionNumber revisionNumber, boolean isBinary) {
    if (isBinary) {
      return new CvsUpToDateBinaryRevision(path, revisionNumber);
    }
    return new CvsUpToDateRevision(path, revisionNumber);
  }

  private static boolean isInContent(VirtualFile file) {
    return file == null || !FileTypeManager.getInstance().isFileIgnored(file.getName());
  }

  private static DirectoryContent getDirectoryContent(VirtualFile directory, final Runnable checkCanceled) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Retrieving directory content for " + directory);
    }
    CvsInfo cvsInfo = CvsEntriesManager.getInstance().getCvsInfoFor(directory);
    DirectoryContent result = new DirectoryContent(cvsInfo);

    VirtualFile[] children = CvsVfsUtil.getChildrenOf(directory);
    if (children == null) children = VirtualFile.EMPTY_ARRAY;

    Collection<Entry> entries = cvsInfo.getEntries();

    HashMap<String, VirtualFile> nameToFileMap = new HashMap<String, VirtualFile>();
    for (VirtualFile child : children) {
      nameToFileMap.put(child.getName(), child);
    }

    for (final Entry entry : entries) {
      checkCanceled.run();
      String fileName = entry.getFileName();
      if (entry.isDirectory()) {
        if (nameToFileMap.containsKey(fileName)) {
          VirtualFile virtualFile = nameToFileMap.get(fileName);
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
          VirtualFile virtualFile = nameToFileMap.get(fileName);
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
      checkCanceled.run();
      VirtualFile unknown = nameToFileMap.get(name);
      if (unknown.isDirectory()) {
        if (isInContent(unknown)) {
          result.addUnknownDirectory(unknown);
        }
      }
      else {
        if (isInContent(unknown)) {
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

  private class CvsUpToDateRevision implements ContentRevision {
    protected final FilePath myPath;
    private final VcsRevisionNumber myRevisionNumber;
    private String myContent;

    protected CvsUpToDateRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      myRevisionNumber = revisionNumber;
      myPath = path;
    }

    @Nullable
    public String getContent() throws VcsException {
      if (myContent == null) {
        try {
          byte[] fileBytes = getUpToDateBinaryContent();
          myContent = fileBytes == null ? null : new String(fileBytes, myPath.getCharset().name());
        }
        catch (CannotFindCvsRootException e) {
          myContent = null;
        }
        catch (UnsupportedEncodingException e) {
          myContent = null;
        }
      }
      return myContent;
    }

    @Nullable
    protected byte[] getUpToDateBinaryContent() throws CannotFindCvsRootException {
      VirtualFile virtualFile = myPath.getVirtualFile();
      byte[] result = null;
      if (virtualFile != null) {
        result = getLastUpToDateContentFor(virtualFile);
      }
      if (result == null) {
        String createVersionFile = null;
        final GetFileContentOperation operation;
        if (virtualFile != null) {
          // todo maybe refactor where data lives
          Entry entry = myEntriesManager.getEntryFor(virtualFile.getParent(), virtualFile.getName());
          if (entry != null && entry.isResultOfMerge()) {
            createVersionFile = entry.getRevision();
          }
          
          operation = GetFileContentOperation.createForFile(virtualFile, SimpleRevision.createForTheSameVersionOf(virtualFile));
        }
        else {
          operation = GetFileContentOperation.createForFile(myPath);
        }
        if (operation.getRoot().isOffline()) return null;
        CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
        result = operation.tryGetFileBytes();

        if (result != null && createVersionFile != null) {
          // cache in CVS area to reduce remote requests number (old revisions are deleted)
          CvsUtil.storeContentForRevision(virtualFile, createVersionFile, result);
        }
      }
      return result;
    }

    @NotNull
    public FilePath getFile() {
      return myPath;
    }

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
    private byte[] myBinaryContent;

    public CvsUpToDateBinaryRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      super(path, revisionNumber);
    }

    @Nullable
    public byte[] getBinaryContent() throws VcsException {
      if (myBinaryContent == null) {
        try {
          myBinaryContent = getUpToDateBinaryContent();
        }
        catch (CannotFindCvsRootException e) {
          throw new VcsException(e);
        }
      }
      return myBinaryContent;
    }

    @NonNls
    public String toString() {
      return "CvsUpToDateBinaryRevision:" + myPath;
    }
  }
}
