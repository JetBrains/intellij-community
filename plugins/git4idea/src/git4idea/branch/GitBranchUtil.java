// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import git4idea.*;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRefUtil;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchActionsUtilKt;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GitBranchUtil {

  private static final Logger LOG = Logger.getInstance(GitBranchUtil.class);

  // The name that specifies that git is on specific commit rather then on some branch ({@value})
  private static final String NO_BRANCH_NAME = "(no branch)"; //NON-NLS

  private GitBranchUtil() { }

  /**
   * Returns the tracking information about the given branch in the given repository,
   * or null if there is no such information (i.e. if the branch doesn't have a tracking branch).
   */
  public static @Nullable GitBranchTrackInfo getTrackInfoForBranch(@NotNull GitRepository repository, @NotNull GitLocalBranch branch) {
    return getTrackInfo(repository, branch.getName());
  }

  public static @Nullable GitBranchTrackInfo getTrackInfo(@NotNull GitRepository repository, @NotNull @NonNls String localBranchName) {
    return repository.getBranchTrackInfo(localBranchName);
  }

  static @NlsSafe @NotNull String getCurrentBranchOrRev(@NotNull Collection<? extends GitRepository> repositories) {
    if (repositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(repositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !repositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = repositories.iterator().next();
      return getBranchNameOrRev(repository);
    }
  }

  public static @NotNull Collection<String> convertBranchesToNames(@NotNull Collection<? extends GitBranch> branches) {
    return ContainerUtil.map(branches, GitBranch::getName);
  }

  public static @NotNull List<String> getAllTags(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.TAG);
    h.addParameters("-l");
    h.setSilent(true);
    h.setEnableInteractiveCallbacks(false);

    List<String> tags = new ArrayList<>();
    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        if (!line.isEmpty()) tags.add(line);
      }
    });

    GitCommandResult result = Git.getInstance().runCommandWithoutCollectingOutput(h);
    result.throwOnError();

    return tags;
  }

  /**
   * Convert {@link GitRemoteBranch GitRemoteBranches} to their names, and remove remote HEAD pointers: origin/HEAD.
   */
  public static @NotNull Collection<String> getBranchNamesWithoutRemoteHead(@NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
    return ContainerUtil.filter(convertBranchesToNames(remoteBranches), input -> !input.equals("HEAD")); //NON-NLS
  }

  public static @NlsSafe @NotNull String stripRefsPrefix(@NotNull @NonNls String branchName) {
    return com.intellij.vcs.git.shared.ref.GitRefUtil.stripRefsPrefix(branchName);
  }

  /**
   * Returns current branch name (if on branch) or current revision otherwise.
   * For fresh repository returns an empty string.
   */
  public static @NlsSafe @NotNull String getBranchNameOrRev(@NotNull GitRepository repository) {
    var repoInfo = repository.getInfo();
    if (repoInfo.isOnBranch()) {
      GitBranch currentBranch = repoInfo.getCurrentBranch();
      assert currentBranch != null;
      return currentBranch.getName();
    }
    else {
      String currentRevision = repoInfo.getCurrentRevision();
      return currentRevision != null ? currentRevision.substring(0, 7) : "";
    }
  }

  /**
   * <p>Shows a message dialog to enter the name of new branch.</p>
   * <p>Optionally allows to not checkout this branch, and just create it.</p>
   *
   * @return the name of the new branch and whether it should be checked out, or {@code null} if user has cancelled the dialog.
   */
  public static @Nullable GitNewBranchOptions getNewBranchNameFromUser(@NotNull Project project,
                                                             @NotNull Collection<? extends GitRepository> repositories,
                                                             @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                                             @Nullable @NlsSafe String initialName) {
    return getNewBranchNameFromUser(project, repositories, dialogTitle, initialName, false);
  }

  public static @Nullable GitNewBranchOptions getNewBranchNameFromUser(@NotNull Project project,
                                                                       @NotNull Collection<? extends GitRepository> repositories,
                                                                       @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                                                       @Nullable @NlsSafe String initialName,
                                                                       boolean showTrackingOption) {
    return new GitNewBranchDialog(project, repositories, dialogTitle, initialName, true, false, showTrackingOption).showAndGetOptions();
  }

  /**
   * Returns the text that is displaying current branch.
   * In the simple case it is just the branch name, but in detached HEAD state it displays the hash or "rebasing master".
   */
  public static @Nls @NotNull String getDisplayableBranchText(@NotNull GitRepository repository) {
    return getDisplayableBranchText(repository, Function.identity());
  }

  public static @Nls @NotNull String getDisplayableBranchText(@NotNull GitRepository repository,
                                                              @NotNull Function<@NotNull @NlsSafe String, @NotNull @NlsSafe String> branchNameTruncator) {
    GitRepository.State state = repository.getState();
    if (state == Repository.State.DETACHED) {
      String detachedStatePresentableText = getDetachedStatePresentableText(repository, branchNameTruncator);
      return detachedStatePresentableText != null ? detachedStatePresentableText : "";
    }

    GitBranch branch = repository.getCurrentBranch();
    String branchName = (branch == null ? "" : branchNameTruncator.apply(branch.getName()));

    if (state == GitRepository.State.MERGING) {
      return GitBundle.message("git.status.bar.widget.text.merge", branchName);
    }
    else if (state == GitRepository.State.REBASING) {
      return GitBundle.message("git.status.bar.widget.text.rebase", branchName);
    }
    else if (state == GitRepository.State.GRAFTING) {
      return GitBundle.message("git.status.bar.widget.text.cherry.pick", branchName);
    }
    else if (state == GitRepository.State.REVERTING) {
      return GitBundle.message("git.status.bar.widget.text.revert", branchName);
    }
    else {
      return branchName;
    }
  }

  private static @Nls String getDetachedStatePresentableText(@NotNull GitRepository repository,
                                                             @NotNull Function<@NotNull @NlsSafe String,
                                                        @NotNull @NlsSafe String> branchNameTruncator) {
    GitReference currentReference = GitRefUtil.getCurrentReference(repository);
    if (currentReference instanceof GitTag) {
      return branchNameTruncator.apply(currentReference.getName());
    }
    else {
      String currentRevision = repository.getCurrentRevision();
      if (currentRevision != null) {
        return DvcsUtil.getShortHash(currentRevision);
      }
      else {
        LOG.warn(String.format("Current revision is null in DETACHED state. isFresh: %s", repository.isFresh()));
        return GitBundle.message("git.status.bar.widget.text.unknown");
      }
    }
  }

  /**
   * @deprecated Prefer {@link #guessWidgetRepository(Project)} or {@link #guessRepositoryForOperation(Project, DataContext)}.
   */
  @Deprecated
  @RequiresEdt
  public static @Nullable GitRepository getCurrentRepository(@NotNull Project project) {
    return getRepositoryOrGuess(project, DvcsUtil.getSelectedFile(project));
  }

  /**
   * @deprecated Prefer {@link #guessRepositoryForOperation(Project, DataContext)}.
   */
  @Deprecated
  @CalledInAny
  public static @Nullable GitRepository getRepositoryOrGuess(@NotNull Project project, @Nullable VirtualFile file) {
    if (project.isDisposed()) return null;
    return DvcsUtil.guessRepositoryForFile(project, GitUtil.getRepositoryManager(project), file,
                                           GitVcsSettings.getInstance(project).getRecentRootPath());
  }

  public static @Nullable GitRepository guessRepositoryForOperation(@NotNull Project project, @NotNull DataContext dataContext) {
    return DvcsUtil.guessRepositoryForOperation(project, GitUtil.getRepositoryManager(project), dataContext);
  }

  public static @Nullable GitRepository guessWidgetRepository(@NotNull Project project, @Nullable VirtualFile selectedFile) {
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    return DvcsUtil.guessWidgetRepository(project, GitUtil.getRepositoryManager(project), settings.getRecentRootPath(), selectedFile);
  }

  public static @Nullable GitRepository guessWidgetRepository(@NotNull Project project, @NotNull DataContext dataContext) {
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    return DvcsUtil.guessWidgetRepository(project, GitUtil.getRepositoryManager(project), settings.getRecentRootPath(), dataContext);
  }

  public static @NotNull Collection<String> getCommonBranches(Collection<? extends GitRepository> repositories,
                                                              boolean local) {
    Collection<String> names;
    if (local) {
      names = convertBranchesToNames(getCommonLocalBranches(repositories));
    }
    else {
      names = getBranchNamesWithoutRemoteHead(getCommonRemoteBranches(repositories));
    }
    return StreamEx.of(names).sorted(StringUtil::naturalCompare).toList();
  }

  public static @NotNull List<GitLocalBranch> getCommonLocalBranches(@NotNull Collection<? extends GitRepository> repositories) {
    return collectCommon(repositories.stream().map(repository -> repository.getBranches().getLocalBranches()));
  }

  public static @NotNull List<GitRemoteBranch> getCommonRemoteBranches(@NotNull Collection<? extends GitRepository> repositories) {
    return collectCommon(repositories.stream().map(repository -> repository.getBranches().getRemoteBranches()));
  }

  public static @NotNull List<GitTag> getCommonTags(@NotNull Collection<? extends GitRepository> repositories) {
    return collectCommon(repositories.stream().map(repository -> repository.getTagHolder().getTags().keySet()));
  }

  public static @NotNull <T> List<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups) {
    return collectCommon(groups, null);
  }

  public static @NotNull <T> List<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups,
                                                   Hash.@Nullable Strategy<? super T> hashingStrategy) {
    List<T> common = new ArrayList<>();
    boolean[] firstGroup = {true};

    groups.forEach(values -> {
      if (firstGroup[0]) {
        firstGroup[0] = false;
        common.addAll(values);
      }
      else {
        Set<T> c = hashingStrategy == null ? new HashSet<>(values) : new ObjectOpenCustomHashSet<>(values, hashingStrategy);
        common.retainAll(c);
      }
    });

    return common;
  }

  public static @NotNull <T extends GitReference> List<T> sortBranchesByName(@NotNull Collection<? extends T> branches) {
    return branches.stream()
      .sorted(Comparator.comparing(GitReference::getFullName, NaturalComparator.INSTANCE))
      .collect(Collectors.toList());
  }

  public static @NotNull List<String> sortBranchNames(@NotNull Collection<String> branchNames) {
    return ContainerUtil.sorted(branchNames, NaturalComparator.INSTANCE);
  }

  /**
   * List branches containing a commit. Specify null if no commit filtering is needed.
   */
  @RequiresBackgroundThread
  public static @NotNull Collection<String> getBranches(@NotNull Project project, @NotNull VirtualFile root, boolean localWanted,
                                                        boolean remoteWanted, @Nullable String containingCommit) throws VcsException {
    // preparing native command executor
    final GitLineHandler handler = new GitLineHandler(project, root, GitCommand.BRANCH);
    handler.setSilent(true);
    handler.addParameters("--no-color");
    boolean remoteOnly = false;
    if (remoteWanted && localWanted) {
      handler.addParameters("-a");
      remoteOnly = false;
    }
    else if (remoteWanted) {
      handler.addParameters("-r");
      remoteOnly = true;
    }
    if (containingCommit != null) {
      handler.addParameters("--contains", containingCommit);
    }
    final String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    if (output.trim().isEmpty()) {
      // the case after git init and before first commit - there is no branch and no output, and we'll take refs/heads/master
      String head;
      try {
        File headFile = GitUtil.getRepositoryForRoot(project, root).getRepositoryFiles().getHeadFile();
        head = FileUtil.loadFile(headFile, StandardCharsets.UTF_8).trim();
        final String prefix = "ref: refs/heads/"; //NON-NLS
        return head.startsWith(prefix) ?
               Collections.singletonList(head.substring(prefix.length())) :
               Collections.emptyList();
      }
      catch (IOException e) {
        LOG.info(e);
        return Collections.emptyList();
      }
    }

    Collection<String> branches = new ArrayList<>();
    // standard situation. output example:
    //  master
    //* my_feature
    //  remotes/origin/HEAD -> origin/master
    //  remotes/origin/eap
    //  remotes/origin/feature
    //  remotes/origin/master
    // also possible:
    //* (no branch)
    // and if we call with -r instead of -a, remotes/ prefix is omitted:
    // origin/HEAD -> origin/master
    final String[] split = output.split("\n");
    for (String b : split) {
      b = b.substring(2).trim();
      if (b.equals(NO_BRANCH_NAME)) {
        continue;
      }

      String remotePrefix = null;
      if (b.startsWith("remotes/")) { //NON-NLS
        remotePrefix = "remotes/"; //NON-NLS
      }
      else if (b.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
        remotePrefix = GitBranch.REFS_REMOTES_PREFIX;
      }
      boolean isRemote = remotePrefix != null || remoteOnly;
      if (isRemote) {
        if (!remoteOnly) {
          b = b.substring(remotePrefix.length());
        }
        final int idx = b.indexOf("HEAD ->"); //NON-NLS
        if (idx > 0) {
          continue;
        }
      }
      branches.add(b);
    }
    return branches;
  }

  /**
   * Checks whether branch names passed through arguments are the same
   * considering OS file system case sensitivity.
   */
  public static boolean equalBranches(@Nullable @NonNls String branchA, @Nullable @NonNls String branchB) {
    return StringUtilRt.equal(branchA, branchB, SystemInfo.isFileSystemCaseSensitive);
  }

  public static void updateBranches(@NotNull Project project,
                                    @NotNull List<? extends GitRepository> repositories,
                                    @NotNull List<String> localBranchNames) {
    GitBranchActionsUtilKt.updateBranches(project, repositories, localBranchNames);
  }
}
