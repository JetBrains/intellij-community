package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsFile;
import com.intellij.openapi.localVcs.LvcsRevision;
import com.intellij.openapi.progress.ProgressIndicator;
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    final DirectoryContent dirContent = CvsStatusProvider.getDirectoryContent(dir, myVcs.getProject());

    for (VirtualFile file : dirContent.getUnknownFiles()) {
      builder.processUnversionedFile(file);
    }
    for(VirtualFile file: dirContent.getIgnoredFiles()) {
      builder.processIgnoredFile(file);
    }

    for(Entry entry: dirContent.getDeletedDirectories()) {
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
    processStatus(filePath, dir.findChild(filePath.getName()), status, number, builder);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder) {
    final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir, entry.getFileName());
    final FileStatus status = CvsStatusProvider.getStatus(file, entry);
    final VcsRevisionNumber number = new CvsRevisionNumber(entry.getRevision());
    processStatus(filePath, file, status, number, builder);
  }

  private void processStatus(final FilePath filePath,
                             final VirtualFile file,
                             final FileStatus status,
                             final VcsRevisionNumber number,
                             final ChangelistBuilder builder) {
    if (status == FileStatus.NOT_CHANGED) {
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        builder.processChange(new Change(new CvsUpToDateRevision(filePath, number), new CurrentContentRevision(filePath), FileStatus.MODIFIED));
      }
      return;
    }
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      builder.processChange(new Change(new CvsUpToDateRevision(filePath, number), new CurrentContentRevision(filePath), status));
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, new CurrentContentRevision(filePath), status));
    }
    else if (status == FileStatus.DELETED) {
      builder.processChange(new Change(new CvsUpToDateRevision(filePath, number), null, status));
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
  public String getLastUpToDateContentFor(VirtualFile vFile, boolean quietly) throws VcsException {
    if (!myEntriesManager.isActive()) return null;
    try {
      LvcsFile file = LocalVcs.getInstance(myVcs.getProject()).findFile(CvsVfsUtil.getPathFor(vFile));
      if (file != null) {
        LvcsRevision revision = file.getRevision();
        long upToDateTime = getUpToDateTimeForFile(vFile);
        while (revision != null) {
          if (CvsStatusProvider.timeStampsAreEqual(upToDateTime, revision.getDate())) {
            LvcsFile lvcsFile = (LvcsFile)revision.getObject();
            byte[] byteContent = lvcsFile.getByteContent(revision.getImplicitLabel());
            if (byteContent == null) return null;
            return new String(byteContent, vFile.getCharset().name());
          }
          revision = revision.getPrevRevision();
        }

        if (quietly) {
          return null;
        } else {
          return loadContentFromCvs(vFile);
        }
      }
      else {
        if (quietly) {
          return null;
        } else {
          return loadContentFromCvs(vFile);
        }
      }
    }
    catch (IOException e) {
      if (!quietly) {
        LOG.error(e);
        return loadContentFromCvs(vFile);
      } else {
        return null;
      }
    }
  }

  long getUpToDateTimeForFile(VirtualFile vFile) {
    Entry entry = myEntriesManager.getEntryFor(CvsVfsUtil.getParentFor(vFile), vFile.getName());
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

  @Nullable
  private String loadContentFromCvs(final VirtualFile vFile) throws VcsException {
    try {
      final GetFileContentOperation operation = GetFileContentOperation.createForFile(vFile);
      CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
      final byte[] fileBytes = operation.getFileBytes();
      return fileBytes == null ? null : new String(fileBytes, vFile.getCharset().name());
    }
    catch (CannotFindCvsRootException e) {
      throw new VcsException(e);
    }
    catch (UnsupportedEncodingException e) {
      throw new VcsException(e);
    }
  }

  private class CvsUpToDateRevision implements ContentRevision {
    private FilePath myPath;
    private VcsRevisionNumber myRevisionNumber;
    private String myContent;

    public CvsUpToDateRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      myRevisionNumber = revisionNumber;
      myPath = path;
    }

    @Nullable
    public String getContent() {
      if (myContent == null) {
        try {
          VirtualFile virtualFile = myPath.getVirtualFile();
          if (virtualFile != null) {
            myContent = getLastUpToDateContentFor(virtualFile, true);
          }
          if (myContent == null) {
            final GetFileContentOperation operation;
            if (virtualFile != null) {
              operation = GetFileContentOperation.createForFile(virtualFile, SimpleRevision.createForTheSameVersionOf(virtualFile));
            }
            else {
              operation = GetFileContentOperation.createForFile(myPath);
            }
            if (operation.getRoot().isOffline()) return null;
            CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
            final byte[] fileBytes = operation.tryGetFileBytes();
            myContent = fileBytes == null ? null : new String(fileBytes, myPath.getCharset().name());
          }
        }
        catch (Exception e) {
          myContent = null;
        }
      }
      return myContent;
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
}
