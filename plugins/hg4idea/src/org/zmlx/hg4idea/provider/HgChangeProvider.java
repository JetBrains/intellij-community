// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.*;

import java.util.*;

public class HgChangeProvider implements ChangeProvider {

  private final Project myProject;
  private final VcsKey myVcsKey;

  private static final EnumMap<HgFileStatusEnum, HgChangeProcessor> PROCESSORS =
    new EnumMap<HgFileStatusEnum, HgChangeProcessor>(HgFileStatusEnum.class);

  static {
    PROCESSORS.put(HgFileStatusEnum.ADDED, HgChangeProcessor.ADDED);
    PROCESSORS.put(HgFileStatusEnum.DELETED, HgChangeProcessor.DELETED);
    PROCESSORS.put(HgFileStatusEnum.IGNORED, HgChangeProcessor.IGNORED);
    PROCESSORS.put(HgFileStatusEnum.MISSING, HgChangeProcessor.MISSING);
    PROCESSORS.put(HgFileStatusEnum.COPY, HgChangeProcessor.COPIED);
    PROCESSORS.put(HgFileStatusEnum.MODIFIED, HgChangeProcessor.MODIFIED);
    PROCESSORS.put(HgFileStatusEnum.UNMODIFIED, HgChangeProcessor.UNMODIFIED);
    PROCESSORS.put(HgFileStatusEnum.UNVERSIONED, HgChangeProcessor.UNVERSIONED);
  }

  public HgChangeProvider(Project project, VcsKey vcsKey) {
    this.myProject = project;
    this.myVcsKey = vcsKey;
  }

  public void getChanges(VcsDirtyScope dirtyScope, ChangelistBuilder builder,
    ProgressIndicator progress, ChangeListManagerGate addGate) throws VcsException {
    Set<VirtualFile> processedRoots = new LinkedHashSet<VirtualFile>();
    for (FilePath filePath : dirtyScope.getRecursivelyDirtyDirectories()) {
      process(builder, filePath, processedRoots);
    }
    for (FilePath filePath : dirtyScope.getDirtyFiles()) {
      process(builder, filePath, processedRoots);
    }

    processUnsavedChanges(builder, dirtyScope.getDirtyFilesNoExpand());
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(List<VirtualFile> files) {
  }

  private void process(ChangelistBuilder builder,
    FilePath filePath, Set<VirtualFile> processedRoots) {
    VirtualFile repo = VcsUtil.getVcsRootFor(myProject, filePath);
    if (repo == null || processedRoots.contains(repo)) {
      return;
    }
    Set<HgChange> hgChanges = new HgStatusCommand(myProject).execute(repo);
    if (hgChanges == null || hgChanges.isEmpty()) {
      return;
    }
    sendChanges(builder, hgChanges,
      new HgResolveCommand(myProject).list(repo),
      new HgWorkingCopyRevisionsCommand(myProject).identify(repo),
      new HgWorkingCopyRevisionsCommand(myProject).firstParent(repo)
    );
    processedRoots.add(repo);
  }

  private void sendChanges(ChangelistBuilder builder, Set<HgChange> changes,
    Map<HgFile, HgResolveStatusEnum> resolveStatus, HgRevisionNumber workingRevision,
    HgRevisionNumber parentRevision) {
    for (HgChange change : changes) {
      HgFile afterFile = change.afterFile();
      HgFile beforeFile = change.beforeFile();
      HgFileStatusEnum status = change.getStatus();

      if (resolveStatus.containsKey(afterFile)
        && resolveStatus.get(afterFile) == HgResolveStatusEnum.UNRESOLVED) {
        builder.processChange(
          new Change(
            new HgContentRevision(myProject, beforeFile, parentRevision),
            HgCurrentContentRevision.create(afterFile, workingRevision),
            FileStatus.MERGED_WITH_CONFLICTS
          ), myVcsKey);
        continue;
      }

      if (isDeleteOfCopiedFile(change, changes)) {
        // Don't register the 'delete' change for renamed or moved files; IDEA already handles these
        // itself.
        continue;
      }

      HgChangeProcessor processor = PROCESSORS.get(status);
      if (processor != null) {
        processor.process(myProject, myVcsKey, builder,
          workingRevision, parentRevision, beforeFile, afterFile);
      }
    }
  }

  private boolean isDeleteOfCopiedFile(HgChange change, Set<HgChange> changes) {
    if (change.getStatus().equals(HgFileStatusEnum.DELETED)) {
      for (HgChange otherChange : changes) {
        if (otherChange.getStatus().equals(HgFileStatusEnum.COPY) &&
          otherChange.beforeFile().equals(change.afterFile())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Finds modified but unsaved files in the given list of dirty files and notifies the builder about MODIFIED changes.
   */
  public void processUnsavedChanges(ChangelistBuilder builder, Set<FilePath> dirtyFiles) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    for (FilePath filePath : dirtyFiles) {
      final VirtualFile vf = filePath.getVirtualFile();
      if (vf != null &&  fileDocumentManager.isFileModifiedAndDocumentUnsaved(vf)) {
        final VirtualFile root = vcsManager.getVcsRootFor(vf);
        if (root != null) {
          final HgRevisionNumber beforeRevisionNumber = new HgWorkingCopyRevisionsCommand(myProject).identify(root);
          builder.processChange(new Change(new HgContentRevision(myProject, new HgFile(myProject, vf), beforeRevisionNumber),
                                           CurrentContentRevision.create(filePath), FileStatus.MODIFIED), myVcsKey);
        }
      }
    }
  }


  private enum HgChangeProcessor {
    ADDED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        processChange(
          null,
          HgCurrentContentRevision.create(afterFile, currentNumber),
          FileStatus.ADDED,
          builder,
          vcsKey
        );
      }
    },

    DELETED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        processChange(
          new HgContentRevision(project, beforeFile, parentRevision),
          null,
          FileStatus.DELETED,
          builder,
          vcsKey
        );
      }
    },

    IGNORED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        builder.processIgnoredFile(VcsUtil.getVirtualFile(afterFile.getFile()));
      }
    },

    MISSING() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
                   HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
                   HgFile beforeFile, HgFile afterFile) {
        builder.processLocallyDeletedFile(new LocallyDeletedChange(beforeFile.toFilePath()));
      }
    },

    COPIED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        if (beforeFile.getFile().exists()) {
          // The original file exists so this is a duplication of the file.
          // Don't create the before ContentRevision or IDEA will think
          // this was a rename.
          processChange(
            null,
            HgCurrentContentRevision.create(afterFile, currentNumber),
            FileStatus.ADDED,
            builder,
            vcsKey
          );
        } else {
          // The original file does not exists so this is a rename.
          processChange(
            new HgContentRevision(project, beforeFile, parentRevision),
            HgCurrentContentRevision.create(afterFile, currentNumber),
            FileStatus.MODIFIED,
            builder,
            vcsKey
          );
        }
      }
    },

    MODIFIED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        processChange(
          new HgContentRevision(project, beforeFile, parentRevision),
          HgCurrentContentRevision.create(afterFile, currentNumber),
          FileStatus.MODIFIED,
          builder,
          vcsKey
        );
      }
    },

    UNMODIFIED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        //DO NOTHING
      }
    },

    UNVERSIONED() {
      @Override
      void process(Project project, VcsKey vcsKey, ChangelistBuilder builder,
        HgRevisionNumber currentNumber, HgRevisionNumber parentRevision,
        HgFile beforeFile, HgFile afterFile) {
        builder.processUnversionedFile(VcsUtil.getVirtualFile(afterFile.getFile()));
      }
    };

    abstract void process(
      Project project,
      VcsKey vcsKey,
      ChangelistBuilder builder,
      HgRevisionNumber currentNumber,
      HgRevisionNumber parentRevision,
      HgFile beforeFile,
      HgFile afterFile
    );

    final void processChange(ContentRevision contentRevisionBefore,
      ContentRevision contentRevisionAfter, FileStatus fileStatus,
      ChangelistBuilder builder, VcsKey vcsKey) {
      if (contentRevisionBefore == null && contentRevisionAfter == null) {
        return;
      }
      builder.processChange(
        new Change(contentRevisionBefore, contentRevisionAfter, fileStatus),
        vcsKey
      );
    }
  }
}