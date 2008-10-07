package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.commands.GitCommand;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Git repository change provider
 */
public class GitChangeProvider implements ChangeProvider {
  /**
   * the project
   */
  private final Project project;
  /**
   * the VCS settings
   */
  private final GitVcsSettings settings;

  /**
   * A constructor
   *
   * @param project  a project
   * @param settings a VCS settings
   */
  public GitChangeProvider(@NotNull Project project, @NotNull GitVcsSettings settings) {
    this.project = project;
    this.settings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public void getChanges(VcsDirtyScope dirtyScope, ChangelistBuilder builder, ProgressIndicator progress) throws VcsException {
    Collection<VirtualFile> roots = dirtyScope.getAffectedContentRoots();
    for (VirtualFile root : roots) {
      GitCommand command = new GitCommand(project, settings, root);
      // TODO Set<FilePath> fpaths = dirtyScope.getDirtyFiles();
      //Set<GitVirtualFile> files = command.virtualFiles(fpaths);
      Set<GitVirtualFile> files = command.changedFiles();
      for (GitVirtualFile file : files) {
        getChange(builder, file);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModifiedDocumentTrackingRequired() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void doCleanup(final List<VirtualFile> files) {
  }

  /**
   * Get change from virtual file into the builder
   *
   * @param builder the changelist builder
   * @param file    the file change information
   */
  private void getChange(ChangelistBuilder builder, GitVirtualFile file) {
    FilePath path = VcsUtil.getFilePath(file.getPath());
    VirtualFile vfile = VcsUtil.getVirtualFile(file.getPath());
    ContentRevision beforeRev = new GitContentRevision(VcsUtil.getFilePath(file.getBeforePath()),
                                                       new GitRevisionNumber(GitRevisionNumber.TIP, new Date(file.getModificationStamp())),
                                                       project);
    ContentRevision afterRev = CurrentContentRevision.create(path);

    switch (file.getStatus()) {
      case UNMERGED: {
        builder.processChange(new Change(beforeRev, afterRev, FileStatus.MERGED_WITH_CONFLICTS));
        break;
      }
      case ADDED: {
        builder.processChange(new Change(null, afterRev, FileStatus.ADDED));
        break;
      }
      case DELETED: {
        builder.processChange(new Change(beforeRev, afterRev, FileStatus.DELETED));
        break;
      }
      case COPY: {
        builder.processChange(new Change(null, afterRev, FileStatus.ADDED));
        break;
      }
      case RENAME: {
        builder.processChange(new Change(beforeRev, afterRev, FileStatus.MODIFIED));
        break;
      }
      case MODIFIED: {
        builder.processChange(new Change(beforeRev, afterRev, FileStatus.MODIFIED));
        break;
      }
      case UNMODIFIED: {
        break;
      }
      case UNVERSIONED: {
        builder.processUnversionedFile(vfile);
        break;
      }
      default: {
        builder.processChange(new Change(null, afterRev, FileStatus.UNKNOWN));
      }
    }
  }
}
