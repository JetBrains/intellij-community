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
package org.zmlx.hg4idea;

import com.intellij.dvcs.branch.DvcsTaskHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.branch.HgBranchUtil;
import org.zmlx.hg4idea.command.HgBookmarkCommand;
import org.zmlx.hg4idea.command.HgCommitCommand;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgReferenceValidator;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;

public class HgTaskHandler extends DvcsTaskHandler<HgRepository> {

  private HgReferenceValidator myNameValidator;

  public HgTaskHandler(@NotNull HgRepositoryManager repositoryManager,
                       @NotNull Project project) {
    super(repositoryManager, project, "bookmark");
    myNameValidator = HgReferenceValidator.getInstance();
  }

  @Override
  protected void checkout(@NotNull String taskName, @NotNull List<HgRepository> repos, @Nullable Runnable callInAwtLater) {
    HgUpdateCommand.updateTo(
      !HgBranchUtil.getCommonBookmarks(repos).contains(taskName) ? "head() and not bookmark() and branch(" + taskName + ")" : taskName,
      repos, callInAwtLater);
  }

  @Override
  protected void checkoutAsNewBranch(@NotNull String name, @NotNull List<HgRepository> repositories) {
    HgBookmarkCommand.createBookmark(repositories, name, true);
  }

  @Override
  protected String getActiveBranch(HgRepository repository) {
    String bookmark = repository.getCurrentBookmark();
    return bookmark == null ? repository.getCurrentBranch() : bookmark;
  }

  @NotNull
  @Override
  protected Iterable<String> getAllBranches(@NotNull HgRepository repository) {
    //be careful with equality names of branches/bookmarks =(
    return ContainerUtil.concat(HgUtil.getSortedNamesWithoutHashes(repository.getBookmarks()), repository.getOpenedBranches());
  }

  @Override
  protected void mergeAndClose(@NotNull final String branch, @NotNull final List<HgRepository> repositories) {
    String bookmarkRevisionArg = "bookmark(\"" + branch + "\")";
    FileDocumentManager.getInstance().saveAllDocuments();
    final UpdatedFiles updatedFiles = UpdatedFiles.create();
    for (final HgRepository repository : repositories) {
      HgMergeCommand.mergeWith(repository, bookmarkRevisionArg, updatedFiles, new Runnable() {

        @Override
        public void run() {
          Project project = repository.getProject();
          VirtualFile repositoryRoot = repository.getRoot();
          try {
            new HgCommitCommand(project, repository, "Automated merge with " + branch).execute();
            new HgBookmarkCommand(project, repositoryRoot, branch).deleteBookmark();
          }
          catch (HgCommandException e) {
              HgErrorUtil.handleException(project, e);
          }
          catch (VcsException e) {
            VcsNotifier.getInstance(project)
              .notifyError("Exception during merge commit with " + branch, e.getMessage());
          }
        }
      });
    }
  }

  @Override
  protected boolean hasBranch(@NotNull HgRepository repository, @NotNull String name) {
    return HgUtil.getNamesWithoutHashes(repository.getBookmarks()).contains(name) || repository.getOpenedBranches().contains(name);
  }

  @Override
  public boolean isBranchNameValid(@NotNull String branchName) {
    return myNameValidator.checkInput(branchName);
  }

  @NotNull
  @Override
  public String cleanUpBranchName(@NotNull String suggestedName) {
    return myNameValidator.cleanUpBranchName(suggestedName);
  }
}
