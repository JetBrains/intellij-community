package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.checkinProject.CvsRollbacker;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class CvsChangeProvider implements ChangeProvider {
  private CvsVcs2 myVcs;

  public CvsChangeProvider(final CvsVcs2 vcs) {
    myVcs = vcs;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) {
    for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
      processEntriesIn(path.getVirtualFile(), dirtyScope, builder, true);
    }

    for (FilePath path : dirtyScope.getDirtyFiles()) {
      if (path.isDirectory()) {
        processEntriesIn(path.getVirtualFile(), dirtyScope, builder, false);
      }
      else {
        processFile(path, builder);
      }
    }
  }

  public List<VcsException> rollbackChanges(List<Change> changes) {
    List<VcsException> exceptions = new ArrayList<VcsException>();

    CvsRollbacker rollbacker = new CvsRollbacker(myVcs.getProject());
    for (Change change : changes) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile parent = filePath.getVirtualFileParent();
      String name = filePath.getName();

      try {
        switch (change.getType()) {
          case DELETED:
            rollbacker.rollbackFileDeleting(parent, name);
            break;

          case MODIFICATION:
            rollbacker.rollbackFileModifying(parent, name);
            break;

          case MOVED:
            rollbacker.rollbackFileCreating(parent, name);
            break;

          case NEW:
            rollbacker.rollbackFileCreating(parent, name);
            break;
        }
      }
      catch (IOException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  private void processEntriesIn(VirtualFile dir, VcsDirtyScope scope, ChangelistBuilder builder, boolean recursively) {
    if (!scope.belongsTo(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir))) return;
    final DirectoryContent dirContent = CvsStatusProvider.getDirectoryContent(dir, scope.getScopeModule());

    for (VirtualFile file : dirContent.getUnknownFiles()) {
      builder.processUnversionedFile(file);
    }

    /*
    final Collection<VirtualFile> unknownDirs = dirContent.getUnknownDirectories();
    for (VirtualFile file : unknownDirs) {
      builder.processUnversionedFile(file);
    }
    */

    for (Entry deletedEntry : dirContent.getDeletedFiles()) {
      final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir, deletedEntry.getFileName());
      builder.processChange(new Change(new CvsUpToDateRevision(filePath), null));
    }

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
    processStatus(filePath, status, builder);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder) {
    final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir, entry.getFileName());
    processStatus(filePath, CvsStatusProvider.getStatus(file, entry), builder);
  }

  private void processStatus(final FilePath filePath, final FileStatus status, final ChangelistBuilder builder) {
    if (status == FileStatus.NOT_CHANGED) return;
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      builder.processChange(new Change(new CvsUpToDateRevision(filePath), new CurrentContentRevision(filePath)));
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, new CurrentContentRevision(filePath)));
    }
    else if (status == FileStatus.DELETED) {
      builder.processChange(new Change(new CvsUpToDateRevision(filePath), null));
    }
  }

  private class CvsUpToDateRevision implements ContentRevision {
    private FilePath myPath;

    public CvsUpToDateRevision(final FilePath path) {
      myPath = path;
    }

    @Nullable
    public String getContent() {
      final VirtualFile vFile = myPath.getVirtualFile();
      if (vFile == null) return null;
      try {
        return myVcs.getUpToDateRevisionProvider().getLastUpToDateContentFor(vFile, false);
      }
      catch (VcsException e) {
        return null;
      }
    }

    @NotNull
    public FilePath getFile() {
      return myPath;
    }
  }
}
