// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.*;
import git4idea.commands.*;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import git4idea.validators.GitNewBranchNameValidator;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class GitBranchUtil {

  private static final Logger LOG = Logger.getInstance(GitBranchUtil.class);

  // The name that specifies that git is on specific commit rather then on some branch ({@value})
 private static final String NO_BRANCH_NAME = "(no branch)";

  private GitBranchUtil() {}

  /**
   * Returns the tracking information about the given branch in the given repository,
   * or null if there is no such information (i.e. if the branch doesn't have a tracking branch).
   */
  @Nullable
  public static GitBranchTrackInfo getTrackInfoForBranch(@NotNull GitRepository repository, @NotNull GitLocalBranch branch) {
    return getTrackInfo(repository, branch.getName());
  }

  @Nullable
  public static GitBranchTrackInfo getTrackInfo(@NotNull GitRepository repository, @NotNull String localBranchName) {
    return repository.getBranchTrackInfo(localBranchName);
  }

  @NotNull
  static String getCurrentBranchOrRev(@NotNull Collection<? extends GitRepository> repositories) {
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

  @NotNull
  public static Collection<String> convertBranchesToNames(@NotNull Collection<? extends GitBranch> branches) {
    return ContainerUtil.map(branches, GitBranch::getName);
  }

  /**
   * Returns the current branch in the given repository, or null if either repository is not on the branch, or in case of error.
   * @deprecated Use {@link GitRepository#getCurrentBranch()}
   */
  @Deprecated
  @Nullable
  public static GitLocalBranch getCurrentBranch(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository != null) {
      return repository.getCurrentBranch();
    }
    else {
      LOG.info("getCurrentBranch: Repository is null for root " + root);
      return getCurrentBranchFromGit(project, root);
    }
  }

  @Nullable
  private static GitLocalBranch getCurrentBranchFromGit(@NotNull Project project, @NotNull VirtualFile root) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REV_PARSE);
    handler.addParameters("--abbrev-ref", "HEAD");
    handler.setSilent(true);
    try {
      String name = Git.getInstance().runCommand(handler).getOutputOrThrow();
      if (!name.equals("HEAD")) {
        return new GitLocalBranch(name);
      }
      else {
        return null;
      }
    }
    catch (VcsException e) {
      LOG.info("git rev-parse --abbrev-ref HEAD", e);
      return null;
    }
  }

  @NotNull
  public static List<String> getAllTags(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.TAG);
    h.addParameters("-l");
    h.setSilent(true);

    List<String> tags = new ArrayList<>();
    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        if (line.length() != 0) tags.add(line);
      }
    });

    GitCommandResult result = Git.getInstance().runCommandWithoutCollectingOutput(h);
    result.throwOnError();

    return tags;
  }

  @NotNull
  private static String trackedBranchKey(String branchName) {
    return "branch." + branchName + ".merge";
  }

  @NotNull
  private static String trackedRemoteKey(String branchName) {
    return "branch." + branchName + ".remote";
  }

  /**
   * Get the tracking branch for the given branch, or null if the given branch doesn't track anything.
   * @deprecated Use {@link GitRepository#getBranchTrackInfo(String)}
   */
  @Deprecated
  @Nullable
  public static GitRemoteBranch tracked(@NotNull Project project, @NotNull VirtualFile root, @NotNull String branchName) throws VcsException {
    final HashMap<String, String> result = new HashMap<>();
    GitConfigUtil.getValues(project, root, null, result);
    String remoteName = result.get(trackedRemoteKey(branchName));
    if (remoteName == null) {
      return null;
    }
    String branch = result.get(trackedBranchKey(branchName));
    if (branch == null) {
      return null;
    }

    if (".".equals(remoteName)) {
      return new GitSvnRemoteBranch(branch);
    }

    GitRemote remote = findRemoteByNameOrLogError(project, root, remoteName);
    if (remote == null) return null;
    return new GitStandardRemoteBranch(remote, branch);
  }

  @Nullable
  @Deprecated
  public static GitRemote findRemoteByNameOrLogError(@NotNull Project project, @NotNull VirtualFile root, @NotNull String remoteName) {
    GitRepository repository = GitUtil.getRepositoryForRootOrLogError(project, root);
    if (repository == null) {
      return null;
    }

    GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
    if (remote == null) {
      LOG.warn("Couldn't find remote with name " + remoteName);
      return null;
    }
    return remote;
  }

  /**
   * Convert {@link git4idea.GitRemoteBranch GitRemoteBranches} to their names, and remove remote HEAD pointers: origin/HEAD.
   */
  @NotNull
  public static Collection<String> getBranchNamesWithoutRemoteHead(@NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
    return ContainerUtil.filter(convertBranchesToNames(remoteBranches), input -> !input.equals("HEAD"));
  }

  @NotNull
  public static String stripRefsPrefix(@NotNull String branchName) {
    if (branchName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
      return branchName.substring(GitBranch.REFS_HEADS_PREFIX.length());
    }
    else if (branchName.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
      return branchName.substring(GitBranch.REFS_REMOTES_PREFIX.length());
    }
    else if (branchName.startsWith(GitTag.REFS_TAGS_PREFIX)) {
      return branchName.substring(GitTag.REFS_TAGS_PREFIX.length());
    }
    return branchName;
  }

  /**
   * Returns current branch name (if on branch) or current revision otherwise.
   * For fresh repository returns an empty string.
   */
  @NotNull
  public static String getBranchNameOrRev(@NotNull GitRepository repository) {
    if (repository.isOnBranch()) {
      GitBranch currentBranch = repository.getCurrentBranch();
      assert currentBranch != null;
      return currentBranch.getName();
    } else {
      String currentRevision = repository.getCurrentRevision();
      return currentRevision != null ? currentRevision.substring(0, 7) : "";
    }
  }

  /**
   * <p>Shows a message dialog to enter the name of new branch.</p>
   * <p>Optionally allows to not checkout this branch, and just create it.</p>
   *
   * @return the name of the new branch and whether it should be checked out, or {@code null} if user has cancelled the dialog.
   */
  @Nullable
  public static GitNewBranchOptions getNewBranchNameFromUser(@NotNull Project project,
                                                             @NotNull Collection<? extends GitRepository> repositories,
                                                             @NotNull String dialogTitle,
                                                             @Nullable String initialName) {
    return new GitNewBranchDialog(project, dialogTitle, initialName, GitNewBranchNameValidator.newInstance(repositories)).showAndGetOptions();
  }

  /**
   * Returns the text that is displaying current branch.
   * In the simple case it is just the branch name, but in detached HEAD state it displays the hash or "rebasing master".
   */
  @NotNull
  public static String getDisplayableBranchText(@NotNull GitRepository repository) {
    GitRepository.State state = repository.getState();
    if (state == GitRepository.State.DETACHED) {
      String currentRevision = repository.getCurrentRevision();
      assert currentRevision != null : "Current revision can't be null in DETACHED state, only on the fresh repository.";
      return DvcsUtil.getShortHash(currentRevision);
    }

    String prefix = "";
    if (state == GitRepository.State.MERGING || state == GitRepository.State.REBASING) {
      prefix = state.toString() + " ";
    }
    else if (state == GitRepository.State.GRAFTING) {
      prefix = "Cherry-picking in ";
    }

    GitBranch branch = repository.getCurrentBranch();
    String branchName = (branch == null ? "" : branch.getName());
    return prefix + branchName;
  }

  /**
   * Guesses the Git root on which a Git action is to be invoked.
   * <ol>
   *   <li>
   *     Returns the root for the selected file. Selected file is determined by {@link DvcsUtil#getSelectedFile(Project)}.
   *     If selected file is unknown (for example, no file is selected in the Project View or Changes View and no file is open in the editor),
   *     continues guessing. Otherwise returns the Git root for the selected file. If the file is not under a known Git root,
   *     but there is at least one git root,  continues guessing, otherwise
   *     {@code null} will be returned - the file is definitely determined, but it is not under Git and no git roots exists in project.
   *   </li>
   *   <li>
   *     Takes all Git roots registered in the Project. If there is only one, it is returned.
   *   </li>
   *   <li>
   *     If there are several Git roots,
   *   </li>
   * </ol>
   *
   * <p>
   *   NB: This method has to be accessed from the <b>read action</b>, because it may query
   *   {@link com.intellij.openapi.fileEditor.FileEditorManager#getSelectedTextEditor()}.
   * </p>
   * @param project current project
   * @return Git root that may be considered as "current".
   *         {@code null} is returned if a file not under Git was explicitly selected, if there are no Git roots in the project,
   *         or if the current Git root couldn't be determined.
   */
  @Nullable
  @CalledInAwt
  public static GitRepository getCurrentRepository(@NotNull Project project) {
    return getRepositoryOrGuess(project, DvcsUtil.getSelectedFile(project));
  }

  @Nullable
  public static GitRepository getRepositoryOrGuess(@NotNull Project project, @Nullable VirtualFile file) {
    if (project.isDisposed()) return null;
    return DvcsUtil.guessRepositoryForFile(project, GitUtil.getRepositoryManager(project), file,
                                           GitVcsSettings.getInstance(project).getRecentRootPath());
  }

  @NotNull
  public static Collection<String> getCommonBranches(Collection<? extends GitRepository> repositories,
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

  @NotNull
  public static List<GitLocalBranch> getCommonLocalBranches(@NotNull Collection<? extends GitRepository> repositories) {
    return collectCommon(repositories.stream().map(repository -> repository.getBranches().getLocalBranches()));
  }

  @NotNull
  public static List<GitRemoteBranch> getCommonRemoteBranches(@NotNull Collection<? extends GitRepository> repositories) {
    return collectCommon(repositories.stream().map(repository -> repository.getBranches().getRemoteBranches()));
  }

  @NotNull
  public static <T> List<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups) {
    return collectCommon(groups, ContainerUtil.canonicalStrategy());
  }

  @NotNull
  public static <T> List<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups,
                                          @NotNull TObjectHashingStrategy<? super T> hashingStrategy) {
    List<T> common = new ArrayList<>();
    boolean[] firstGroup = {true};

    groups.forEach(values -> {
      if (firstGroup[0]) {
        firstGroup[0] = false;
        common.addAll(values);
      }
      else {
        common.retainAll(new THashSet<>(values, hashingStrategy));
      }
    });

    return common;
  }

  @NotNull
  public static <T extends GitReference> List<T> sortBranchesByName(@NotNull Collection<? extends T> branches) {
    return branches.stream()
                   .sorted(Comparator.comparing(GitReference::getFullName, NaturalComparator.INSTANCE))
                   .collect(Collectors.toList());
  }

  /**
   * List branches containing a commit. Specify null if no commit filtering is needed.
   */
  @NotNull
  public static Collection<String> getBranches(@NotNull Project project, @NotNull VirtualFile root, boolean localWanted,
                                               boolean remoteWanted, @Nullable String containingCommit) throws VcsException {
    // preparing native command executor
    final GitLineHandler handler = new GitLineHandler(project, root, GitCommand.BRANCH);
    handler.setSilent(true);
    handler.addParameters("--no-color");
    boolean remoteOnly = false;
    if (remoteWanted && localWanted) {
      handler.addParameters("-a");
      remoteOnly = false;
    } else if (remoteWanted) {
      handler.addParameters("-r");
      remoteOnly = true;
    }
    if (containingCommit != null) {
      handler.addParameters("--contains", containingCommit);
    }
    final String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    if (output.trim().length() == 0) {
      // the case after git init and before first commit - there is no branch and no output, and we'll take refs/heads/master
      String head;
      try {
        File headFile = assertNotNull(GitUtil.getRepositoryManager(project).getRepositoryForRoot(root)).getRepositoryFiles().getHeadFile();
        head = FileUtil.loadFile(headFile, StandardCharsets.UTF_8).trim();
        final String prefix = "ref: refs/heads/";
        return head.startsWith(prefix) ?
               Collections.singletonList(head.substring(prefix.length())) :
               Collections.emptyList();
      } catch (IOException e) {
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
      if (b.equals(NO_BRANCH_NAME)) { continue; }

      String remotePrefix = null;
      if (b.startsWith("remotes/")) {
        remotePrefix = "remotes/";
      } else if (b.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
        remotePrefix = GitBranch.REFS_REMOTES_PREFIX;
      }
      boolean isRemote = remotePrefix != null || remoteOnly;
      if (isRemote) {
        if (! remoteOnly) {
          b = b.substring(remotePrefix.length());
        }
        final int idx = b.indexOf("HEAD ->");
        if (idx > 0) {
          continue;
        }
      }
      branches.add(b);
    }
    return branches;
  }
}
