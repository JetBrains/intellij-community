// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.dvcs.branch.DvcsTaskHandler;
import com.intellij.openapi.components.ServiceManager;
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

import java.util.Collections;
import java.util.List;

public class HgTaskHandler extends DvcsTaskHandler<HgRepository> {
  private final HgReferenceValidator myNameValidator;

  public HgTaskHandler(@NotNull Project project) {
    super(ServiceManager.getService(project, HgRepositoryManager.class), project, "bookmark");

    myNameValidator = HgReferenceValidator.getInstance();
  }

  @Override
  protected void checkout(@NotNull String taskName, @NotNull List<? extends HgRepository> repos, @Nullable Runnable callInAwtLater) {
    HgUpdateCommand.updateTo(
      !HgBranchUtil.getCommonBookmarks(repos).contains(taskName) ? "head() and not bookmark() and branch(\"" + taskName + "\")" : taskName,
      repos, callInAwtLater);
  }

  @Override
  protected void checkoutAsNewBranch(@NotNull String name, @NotNull List<? extends HgRepository> repositories) {
    HgBookmarkCommand.createBookmarkAsynchronously(repositories, name, true);
  }

  @Override
  protected String getActiveBranch(HgRepository repository) {
    String bookmark = repository.getCurrentBookmark();
    return bookmark == null ? repository.getCurrentBranch() : bookmark;
  }

  @NotNull
  @Override
  protected Iterable<TaskInfo> getAllBranches(@NotNull HgRepository repository) {
    //be careful with equality names of branches/bookmarks =(
    Iterable<String> names =
      ContainerUtil.concat(HgUtil.getSortedNamesWithoutHashes(repository.getBookmarks()), repository.getOpenedBranches());
    return ContainerUtil.map(names, s -> new TaskInfo(s, Collections.singleton(repository.getPresentableUrl())));
  }

  @Override
  protected void mergeAndClose(@NotNull final String branch, @NotNull final List<? extends HgRepository> repositories) {
    String bookmarkRevisionArg = "bookmark(\"" + branch + "\")";
    FileDocumentManager.getInstance().saveAllDocuments();
    final UpdatedFiles updatedFiles = UpdatedFiles.create();
    for (final HgRepository repository : repositories) {
      HgMergeCommand.mergeWith(repository, bookmarkRevisionArg, updatedFiles, () -> {
        Project project = repository.getProject();
        VirtualFile repositoryRoot = repository.getRoot();
        try {
          new HgCommitCommand(project, repository, "Automated merge with " + branch).executeInCurrentThread();
          HgBookmarkCommand.deleteBookmarkSynchronously(project, repositoryRoot, branch);
        }
        catch (HgCommandException e) {
            HgErrorUtil.handleException(project, e);
        }
        catch (VcsException e) {
          VcsNotifier.getInstance(project)
            .notifyError("Exception during merge commit with " + branch, e.getMessage());
        }
      });
    }
  }

  @Override
  protected boolean hasBranch(@NotNull HgRepository repository, @NotNull TaskInfo name) {
    return HgUtil.getNamesWithoutHashes(repository.getBookmarks()).contains(name.getName()) || repository.getOpenedBranches().contains(name.getName());
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
