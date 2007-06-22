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
import com.intellij.history.LocalHistory;
import com.intellij.history.RevisionTimestampComparator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Date;

/**
 * @author max
 */
public class CvsChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider");

  private CvsVcs2 myVcs;
  private final CvsEntriesManager myEntriesManager;

  public CvsChangeProvider(final CvsVcs2 vcs, CvsEntriesManager entriesManager) {
    myVcs = vcs;
    myEntriesManager = entriesManager;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) {
    for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
      final VirtualFile dir = path.getVirtualFile();
      if (dir != null) {
        processEntriesIn(dir, dirtyScope, builder, true);
      }
      else {
        processFile(path, builder);
      }
    }

    for (FilePath path : dirtyScope.getDirtyFiles()) {
      if (path.isDirectory()) {
        final VirtualFile dir = path.getVirtualFile();
        if (dir != null) {
          processEntriesIn(dir, dirtyScope, builder, false);
        }
        else {
          processFile(path, builder);
        }
      }
      else {
        processFile(path, builder);
      }
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  private void processEntriesIn(@NotNull VirtualFile dir, VcsDirtyScope scope, ChangelistBuilder builder, boolean recursively) {
    if (!scope.belongsTo(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir))) return;
    final DirectoryContent dirContent = CvsStatusProvider.getDirectoryContent(dir);

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

    checkSwitchedDir(dir, builder);

    for (VirtualFileEntry fileEntry : dirContent.getFiles()) {
      processFile(dir, fileEntry.getVirtualFile(), fileEntry.getEntry(), builder);
    }

    if (recursively) {
      for (VirtualFile file : dir.getChildren()) {
        if (file.isDirectory()) {
          processEntriesIn(file, scope, builder, true);
        }
      }
    }
  }


  private void processFile(final FilePath filePath, final ChangelistBuilder builder) {
    final VirtualFile dir = filePath.getVirtualFileParent();
    if (dir == null) return;

    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(dir, filePath.getName());
    final FileStatus status = CvsStatusProvider.getStatus(filePath.getVirtualFile(), entry);
    VcsRevisionNumber number = entry != null ? new CvsRevisionNumber(entry.getRevision()) : VcsRevisionNumber.NULL;
    processStatus(filePath, dir.findChild(filePath.getName()), status, number, entry != null && entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder) {
    final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir, entry.getFileName());
    final FileStatus status = CvsStatusProvider.getStatus(file, entry);
    final VcsRevisionNumber number = new CvsRevisionNumber(entry.getRevision());
    processStatus(filePath, file, status, number, entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void checkSwitchedDir(final VirtualFile dir, final ChangelistBuilder builder) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myVcs.getProject()).getFileIndex();
    VirtualFile parentDir = dir.getParent();
    if (parentDir == null || !fileIndex.isInContent(parentDir)) {
      return;
    }
    final CvsInfo info = CvsEntriesManager.getInstance().getCvsInfoFor(dir);
    if (info.getRepository() == null) {
      // don't report unversioned directories as switched (IDEADEV-17178)
      builder.processUnversionedFile(dir);
      return;
    }
    final String dirTag = info.getStickyTag();
    final CvsInfo parentInfo = CvsEntriesManager.getInstance().getCvsInfoFor(parentDir);
    final String parentDirTag = parentInfo.getStickyTag();
    if (!Comparing.equal(dirTag, parentDirTag)) {
      if (dirTag == null) {
        builder.processSwitchedFile(dir, CvsUtil.HEAD, true);
      }
      else if (dirTag.startsWith(CvsUtil.STICKY_BRANCH_TAG_PREFIX) || dirTag.startsWith(CvsUtil.STICKY_NON_BRANCH_TAG_PREFIX)) {
        final String tag = dirTag.substring(1);
        // a switch between a branch tag and a non-branch tag is not a switch
        if (parentDirTag != null &&
            (parentDirTag.startsWith(CvsUtil.STICKY_BRANCH_TAG_PREFIX) || parentDirTag.startsWith(CvsUtil.STICKY_NON_BRANCH_TAG_PREFIX))) {
          String parentTag = parentDirTag.substring(1);
          if (tag.equals(parentTag)) {
            return;
          }
        }
        builder.processSwitchedFile(dir, CvsBundle.message("switched.tag.format", tag), true);
      }
      else if (dirTag.startsWith(CvsUtil.STICKY_DATE_PREFIX)) {
        try {
          Date date = Entry.STICKY_DATE_FORMAT.parse(dirTag.substring(1));
          builder.processSwitchedFile(dir, CvsBundle.message("switched.date.format", date), true);
        }
        catch (ParseException e) {
          builder.processSwitchedFile(dir, CvsBundle.message("switched.date.format", dirTag.substring(1)), true);
        }
      }
    }
  }

  private void checkSwitchedFile(final FilePath filePath, final ChangelistBuilder builder, final VirtualFile dir, final Entry entry) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myVcs.getProject()).getFileIndex();
    // if content root itself is switched, ignore
    if (!fileIndex.isInContent(dir)) {
      return;
    }
    final String dirTag = CvsEntriesManager.getInstance().getCvsInfoFor(dir).getStickyTag();
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
    if (status == FileStatus.NOT_CHANGED) {
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        builder.processChange(
          new Change(createCvsRevision(filePath, number, isBinary), CurrentContentRevision.create(filePath), FileStatus.MODIFIED));
      }
      return;
    }
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary), CurrentContentRevision.create(filePath), status));
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, CurrentContentRevision.create(filePath), status));
    }
    else if (status == FileStatus.DELETED) {
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary), null, status));
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
    final long upToDateTimestamp = getUpToDateTimeForFile(f);
    RevisionTimestampComparator c = new RevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return CvsStatusProvider.timeStampsAreEqual(upToDateTimestamp, revisionTimestamp);
      }
    };
    return LocalHistory.getByteContent(myVcs.getProject(), f, c);
  }

  public long getUpToDateTimeForFile(@NotNull VirtualFile vFile) {
    Entry entry = myEntriesManager.getEntryFor(vFile.getParent(), vFile.getName());
    if (entry == null) return -1;
    if (entry.isResultOfMerge()) {
      long resultForMerge = CvsUtil.getUpToDateDateForFile(vFile);
      if (resultForMerge > 0) {
        return resultForMerge;
      }
    }

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

  private class CvsUpToDateRevision implements ContentRevision {
    private FilePath myPath;
    private VcsRevisionNumber myRevisionNumber;
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
    protected byte[] getUpToDateBinaryContent() throws VcsException, CannotFindCvsRootException {
      VirtualFile virtualFile = myPath.getVirtualFile();
      byte[] result = null;
      if (virtualFile != null) {
        result = getLastUpToDateContentFor(virtualFile);
      }
      if (result == null) {
        final GetFileContentOperation operation;
        if (virtualFile != null) {
          operation = GetFileContentOperation.createForFile(virtualFile, SimpleRevision.createForTheSameVersionOf(virtualFile));
        }
        else {
          operation = GetFileContentOperation.createForFile(myPath);
        }
        if (operation.getRoot().isOffline()) return null;
        CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
        result = operation.tryGetFileBytes();
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
  }
}
