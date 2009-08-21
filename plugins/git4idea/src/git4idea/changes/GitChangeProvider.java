package git4idea.changes;
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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Git repository change provider
 */
public class GitChangeProvider implements ChangeProvider {
  /**
   * the project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project a project
   */
  public GitChangeProvider(@NotNull Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public void getChanges(final VcsDirtyScope dirtyScope,
                         final ChangelistBuilder builder,
                         final ProgressIndicator progress,
                         final ChangeListManagerGate addGate) throws VcsException {
    Collection<VirtualFile> roots = GitUtil.gitRootsForPaths(dirtyScope.getAffectedContentRoots());
    for (VirtualFile root : roots) {
      ChangeCollector c = new ChangeCollector(myProject, dirtyScope, root);
      for (Change file : c.changes()) {
        builder.processChange(file, GitVcs.getKey());
      }
      for (VirtualFile f : c.unversioned()) {
        builder.processUnversionedFile(f);
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
}
