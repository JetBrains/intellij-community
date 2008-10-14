package git4idea.merge;
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
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Author: Mark Scott
 */

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Merge-changes provider for Git, used by IDEA internal 3-way merge tool
 */
public class GitMergeProvider implements MergeProvider {
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitMergeProvider.class.getName());
  /**
   * The project instance
   */
  private Project myProject;
  /**
   * A revision number for a revision being merged with.
   */
  @NonNls private static final String THEIRS_REVISION = "Theirs";

  /**
   * A merge provider
   *
   * @param project a project for the provider
   */
  public GitMergeProvider(Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public MergeData loadRevisions(VirtualFile file) throws VcsException {
    final MergeData mergeData = new MergeData();
    if (file == null) return mergeData;
    final FilePath path = VcsUtil.getFilePath(file.getPath());

    VcsRunnable runnable = new VcsRunnable() {
      @SuppressWarnings({"ConstantConditions"})
      public void run() throws VcsException {
        GitContentRevision original = new GitContentRevision(path, new GitRevisionNumber(":1"), myProject);
        GitContentRevision current = new GitContentRevision(path, new GitRevisionNumber(":2"), myProject);
        GitContentRevision last = new GitContentRevision(path, new GitRevisionNumber(":3"), myProject);
        mergeData.ORIGINAL = original.getContent().getBytes();
        mergeData.CURRENT = current.getContent().getBytes();
        mergeData.LAST = last.getContent().getBytes();
        mergeData.LAST_REVISION_NUMBER = new GitRevisionNumber(THEIRS_REVISION);
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, GitBundle.message("merge.load.files"), false, myProject);
    return mergeData;
  }


  /**
   * {@inheritDoc}
   */
  public void conflictResolvedForFile(VirtualFile file) {
    if (file == null) return;
    try {
      GitSimpleHandler.addFiles(myProject, GitUtil.getVcsRoot(myProject, file), file).run();
    }
    catch (VcsException e) {
      log.error("Confirming conflict resolution failed", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isBinary(VirtualFile file) {
    return file.getFileType().isBinary();
  }
}