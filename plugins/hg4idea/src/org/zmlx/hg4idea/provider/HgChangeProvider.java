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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgResolveStatusEnum;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class HgChangeProvider implements ChangeProvider {

  private final Project myProject;
  private final VcsKey myVcsKey;

  public static final FileStatus COPIED = FileStatusFactory.getInstance().createFileStatus("COPIED", "Copied", FileStatus.ADDED.getColor());
  public static final FileStatus RENAMED = FileStatusFactory.getInstance().createFileStatus("RENAMED", "Renamed",
                                                                                            new JBColor(JBColor.CYAN.darker().darker(),
                                                                                                        new Color(0x3a8484)));

  private static final EnumMap<HgFileStatusEnum, HgChangeProcessor> PROCESSORS =
    new EnumMap<>(HgFileStatusEnum.class);

  static {
    PROCESSORS.put(HgFileStatusEnum.ADDED, HgChangeProcessor.ADDED);
    PROCESSORS.put(HgFileStatusEnum.DELETED, HgChangeProcessor.DELETED);
    PROCESSORS.put(HgFileStatusEnum.MISSING, HgChangeProcessor.MISSING);
    PROCESSORS.put(HgFileStatusEnum.COPY, HgChangeProcessor.COPIED);
    PROCESSORS.put(HgFileStatusEnum.MODIFIED, HgChangeProcessor.MODIFIED);
    PROCESSORS.put(HgFileStatusEnum.UNMODIFIED, HgChangeProcessor.UNMODIFIED);
    PROCESSORS.put(HgFileStatusEnum.UNVERSIONED, HgChangeProcessor.UNVERSIONED);
  }

  public HgChangeProvider(Project project, VcsKey vcsKey) {
    myProject = project;
    myVcsKey = vcsKey;
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(List<VirtualFile> files) {
  }

  public void getChanges(@NotNull VcsDirtyScope dirtyScope, @NotNull ChangelistBuilder builder,
                         @NotNull ProgressIndicator progress, @NotNull ChangeListManagerGate addGate) throws VcsException {
    if (myProject.isDisposed()) return;
    final Collection<HgChange> changes = new HashSet<>();
    changes.addAll(process(builder, dirtyScope.getRecursivelyDirtyDirectories()));
    changes.addAll(process(builder, dirtyScope.getDirtyFiles()));
    processUnsavedChanges(builder, dirtyScope.getDirtyFilesNoExpand(), changes);
  }

  private Collection<HgChange> process(ChangelistBuilder builder, Collection<FilePath> files) {
    final Set<HgChange> hgChanges = new HashSet<>();
    for (Map.Entry<VirtualFile, Collection<FilePath>> entry : HgUtil.groupFilePathsByHgRoots(myProject, files).entrySet()) {
      VirtualFile repo = entry.getKey();

      final HgRevisionNumber workingRevision = new HgWorkingCopyRevisionsCommand(myProject).identify(repo).getFirst();
      final HgRevisionNumber parentRevision = new HgWorkingCopyRevisionsCommand(myProject).firstParent(repo);
      final Map<HgFile, HgResolveStatusEnum> list = new HgResolveCommand(myProject).getListSynchronously(repo);

      hgChanges.addAll(new HgStatusCommand.Builder(true).ignored(false).build(myProject).executeInCurrentThread(repo, entry.getValue()));
      final HgRepository hgRepo = HgUtil.getRepositoryForFile(myProject, repo);
      if (hgRepo != null && hgRepo.hasSubrepos()) {
        hgChanges.addAll(ContainerUtil.mapNotNull(hgRepo.getSubrepos(), info -> findChange(hgRepo, info)));
      }

      sendChanges(builder, hgChanges, list, workingRevision, parentRevision);
    }
    return hgChanges;
  }

  @Nullable
  private HgChange findChange(@NotNull HgRepository hgRepo, @NotNull HgNameWithHashInfo info) {
    File file = new File(hgRepo.getRoot().getPath(), info.getName());
    VirtualFile virtualSubrepoFile = VfsUtil.findFileByIoFile(file, false);
    HgRepository subrepo = HgUtil.getRepositoryForFile(myProject, virtualSubrepoFile);
    if (subrepo != null && !info.getHash().asString().equals(subrepo.getCurrentRevision())) {
      return new HgChange(new HgFile(hgRepo.getRoot(), VcsUtil.getFilePath(virtualSubrepoFile)), HgFileStatusEnum.MODIFIED);
    }
    return null;
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
            HgContentRevision.create(myProject, beforeFile, parentRevision),
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

  private static boolean isDeleteOfCopiedFile(@NotNull HgChange change, Set<HgChange> changes) {
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
   * Changes contained in {@code alreadyProcessed} are skipped - they have already been processed as modified, or else.
   */
  public void processUnsavedChanges(ChangelistBuilder builder, Set<FilePath> dirtyFiles, Collection<HgChange> alreadyProcessed) {
    // exclude already processed
    for (HgChange c : alreadyProcessed) {
      dirtyFiles.remove(c.beforeFile().toFilePath());
      dirtyFiles.remove(c.afterFile().toFilePath());
    }

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    for (FilePath filePath : dirtyFiles) {
      final VirtualFile vf = filePath.getVirtualFile();
      if (vf != null &&  fileDocumentManager.isFileModified(vf)) {
        final VirtualFile root = vcsManager.getVcsRootFor(vf);
        if (root != null && HgUtil.isHgRoot(root)) {
          final HgRevisionNumber beforeRevisionNumber = new HgWorkingCopyRevisionsCommand(myProject).firstParent(root);
          final ContentRevision beforeRevision = (beforeRevisionNumber == null ? null :
                                                  HgContentRevision.create(myProject, new HgFile(myProject, vf), beforeRevisionNumber));
          builder.processChange(new Change(beforeRevision, CurrentContentRevision.create(filePath), FileStatus.MODIFIED), myVcsKey);
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
          HgContentRevision.create(project, beforeFile, parentRevision),
          null,
          FileStatus.DELETED,
          builder,
          vcsKey
        );
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
          //todo: fix this unexpected status behavior (sometimes added  status instead of copied, and copied instead of renamed )
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
            HgContentRevision.create(project, beforeFile, parentRevision),
            HgCurrentContentRevision.create(afterFile, currentNumber),
            RENAMED,
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
          HgContentRevision.create(project, beforeFile, parentRevision),
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

    static void processChange(ContentRevision contentRevisionBefore,
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
