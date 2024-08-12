// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;
import static git4idea.GitReference.BRANCH_NAME_HASHING_STRATEGY;
import static git4idea.repo.GitRefUtil.*;
import static java.util.Collections.emptyMap;

/**
 * <p>Reads information about the Git repository from Git service files located in the {@code .git} folder.</p>
 * <p>NB: works with {@link File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Git file format.</p>
 */
public class GitRepositoryReader {

  private static final Logger LOG = Logger.getInstance(GitRepositoryReader.class);

  private static final String DETACHED_HEAD = "detached HEAD";

  private final @NotNull Project myProject;
  private final @NotNull GitRepositoryFiles myGitFiles;

  private final @NotNull File myHeadFile;       // .git/HEAD
  private final @NotNull File myRefsHeadsDir;   // .git/refs/heads/
  private final @NotNull File myRefsRemotesDir; // .git/refs/remotes/
  private final @NotNull File myPackedRefsFile; // .git/packed-refs

  GitRepositoryReader(@NotNull Project project, @NotNull GitRepositoryFiles gitFiles) {
    myProject = project;
    myGitFiles = gitFiles;
    myHeadFile = gitFiles.getHeadFile();
    myRefsHeadsDir = gitFiles.getRefsHeadsFile();
    myRefsRemotesDir = gitFiles.getRefsRemotesFile();
    myPackedRefsFile = gitFiles.getPackedRefsPath();
  }

  @NotNull
  GitBranchState readState(@NotNull Collection<GitRemote> remotes) {
    GitBranches branches = readBranches(remotes);
    Map<GitLocalBranch, Hash> localBranches = branches.localBranches;

    HeadInfo headInfo = readHead();
    Repository.State state = readRepositoryState(headInfo.isBranch);

    GitLocalBranch currentBranch;
    String currentRevision;
    if (!headInfo.isBranch || !localBranches.isEmpty()) {
      currentBranch = findCurrentBranch(headInfo, state, localBranches.keySet());
      currentRevision = getCurrentRevision(headInfo, currentBranch == null ? null : localBranches.get(currentBranch));
    }
    else if (headInfo.content != null) {
      currentBranch = new GitLocalBranch(headInfo.content);
      currentRevision = null;
    }
    else {
      currentBranch = null;
      currentRevision = null;
    }
    if (currentBranch == null && currentRevision == null) {
      LOG.warn("Couldn't identify neither current branch nor current revision. Ref specified in .git/HEAD: [" + headInfo.content + "]");
      LOG.debug("Dumping files in .git/refs/, and the content of .git/packed-refs. Debug enabled: " + LOG.isDebugEnabled());
      logDebugAllRefsFiles(myGitFiles);
    }
    return new GitBranchState(currentRevision, currentBranch, state, localBranches, branches.remoteBranches);
  }

  @NotNull
  GitHooksInfo readHooksInfo() {
    boolean hasCommitHook = isExistingExecutableFile(myGitFiles.getPreCommitHookFile()) ||
                            isExistingExecutableFile(myGitFiles.getCommitMsgHookFile());
    boolean hasPushHook = isExistingExecutableFile(myGitFiles.getPrePushHookFile());
    return new GitHooksInfo(hasCommitHook, hasPushHook);
  }

  private static boolean isExistingExecutableFile(@NotNull File file) {
    return file.exists() && file.canExecute();
  }

  boolean hasShallowCommits() {
    File shallowFile = myGitFiles.getShallowFile();
    if (!shallowFile.exists()) {
      return false;
    }

    return shallowFile.length() > 0;
  }

  private static @Nullable String getCurrentRevision(@NotNull HeadInfo headInfo, @Nullable Hash currentBranchHash) {
    String currentRevision;
    if (!headInfo.isBranch) {
      currentRevision = headInfo.content;
    }
    else if (currentBranchHash == null) {
      currentRevision = null;
    }
    else {
      currentRevision = currentBranchHash.asString();
    }
    return currentRevision;
  }

  private @Nullable GitLocalBranch findCurrentBranch(@NotNull HeadInfo headInfo,
                                                     @NotNull Repository.State state,
                                                     @NotNull Set<GitLocalBranch> localBranches) {
    final String currentBranchName = findCurrentBranchName(state, headInfo);
    if (currentBranchName == null) {
      return null;
    }
    final GitLocalBranch currentBranch =
      ContainerUtil.find(localBranches, branch -> BRANCH_NAME_HASHING_STRATEGY.equals(branch.getFullName(), currentBranchName));
    return currentBranch == null ? new GitLocalBranch(currentBranchName) : currentBranch;
  }

  private @NotNull Repository.State readRepositoryState(boolean isOnBranch) {
    if (isMergeInProgress()) {
      return Repository.State.MERGING;
    }
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
    if (!isOnBranch) {
      return Repository.State.DETACHED;
    }
    if (isCherryPickInProgress()) {
      return Repository.State.GRAFTING;
    }
    if (isRevertInProgress()) {
      return Repository.State.REVERTING;
    }
    return Repository.State.NORMAL;
  }

  private @Nullable String findCurrentBranchName(@NotNull Repository.State state, @NotNull HeadInfo headInfo) {
    String currentBranch = null;
    if (headInfo.isBranch) {
      currentBranch = headInfo.content;
    }
    else if (state == Repository.State.REBASING) {
      currentBranch = tryFindRebaseBranch();
    }
    return addRefsHeadsPrefixIfNeeded(currentBranch);
  }

  private @Nullable String tryFindRebaseBranch() {
    String currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseApplyDir());
    if (currentBranch == null) {
      currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseMergeDir());
    }
    return (currentBranch == null || currentBranch.equals(DETACHED_HEAD)) ? null : currentBranch;
  }

  private static @Nullable String readRebaseDirBranchFile(@NonNls File rebaseDir) {
    if (rebaseDir.exists()) {
      File headName = new File(rebaseDir, "head-name");
      if (headName.exists()) {
        return DvcsUtil.tryLoadFileOrReturn(headName, null, CharsetToolkit.UTF8);
      }
    }
    return null;
  }

  private boolean isMergeInProgress() {
    return myGitFiles.getMergeHeadFile().exists();
  }

  private boolean isRebaseInProgress() {
    return myGitFiles.getRebaseApplyDir().exists() || myGitFiles.getRebaseMergeDir().exists();
  }

  private boolean isCherryPickInProgress() {
    return myGitFiles.getCherryPickHead().exists();
  }

  private boolean isRevertInProgress() {
    return myGitFiles.getRevertHead().exists();
  }

  private @NotNull Map<String, String> readPackedBranches() {
    if (!myPackedRefsFile.exists()) {
      return emptyMap();
    }
    try {
      String content = DvcsUtil.tryLoadFile(myPackedRefsFile, CharsetToolkit.UTF8);
      return ContainerUtil.map2MapNotNull(LineTokenizer.tokenize(content, false), GitRefUtil::parseBranchesLine);
    }
    catch (RepoStateException e) {
      return emptyMap();
    }
  }

  private @NotNull GitBranches readBranches(@NotNull Collection<GitRemote> remotes) {
    Map<String, String> data = readBranchRefsFromFiles();
    Map<String, Hash> resolvedRefs = resolveRefs(data);
    return createBranchesFromData(remotes, resolvedRefs);
  }

  private @NotNull Map<String, String> readBranchRefsFromFiles() {
    try {
      // reading from packed-refs first to overwrite values by values from unpacked refs
      Map<String, String> result = new HashMap<>(readPackedBranches());
      result.putAll(readFromRefsFiles(myRefsHeadsDir, REFS_HEADS_PREFIX, myGitFiles));
      result.putAll(readFromRefsFiles(myRefsRemotesDir, REFS_REMOTES_PREFIX, myGitFiles));
      result.remove(REFS_REMOTES_PREFIX + GitUtil.ORIGIN_HEAD);
      return result;
    }
    catch (Throwable e) {
      logDebugAllRefsFiles(myGitFiles);
      LOG.warn("Error reading refs from files", e);
      return emptyMap();
    }
  }

  private static @NotNull GitBranches createBranchesFromData(@NotNull Collection<GitRemote> remotes,
                                                             @NotNull Map<String, Hash> data) {
    Map<GitLocalBranch, Hash> localBranches = new HashMap<>();
    Map<GitRemoteBranch, Hash> remoteBranches = new HashMap<>();
    for (Map.Entry<String, Hash> entry : data.entrySet()) {
      String refName = entry.getKey();
      Hash hash = entry.getValue();

      GitBranch branch = parseBranchRef(remotes, refName);
      if (branch instanceof GitLocalBranch) {
        localBranches.put((GitLocalBranch)branch, hash);
      }
      else if (branch instanceof GitRemoteBranch) {
        remoteBranches.put((GitRemoteBranch)branch, hash);
      }
      else {
        LOG.warn(String.format("Unexpected ref format: %s, %s", refName, branch));
      }
    }
    return new GitBranches(localBranches, remoteBranches);
  }

  public static @Nullable GitBranch parseBranchRef(@NotNull Collection<GitRemote> remotes, String refName) {
    if (refName.startsWith(REFS_HEADS_PREFIX)) {
      return new GitLocalBranch(refName);
    }
    else if (refName.startsWith(REFS_REMOTES_PREFIX)) {
      return GitUtil.parseRemoteBranch(refName, remotes);
    }
    else {
      return null;
    }
  }

  private @NotNull HeadInfo readHead() {
    String headContent;
    try {
      headContent = DvcsUtil.tryLoadFile(myHeadFile, CharsetToolkit.UTF8);
    }
    catch (RepoStateException e) {
      LOG.warn(e);
      return HeadInfo.UNKNOWN;
    }

    Hash hash = parseHash(headContent);
    if (hash != null) {
      return new HeadInfo(false, headContent);
    }

    String target = getTarget(headContent);
    if (target != null) {
      return new HeadInfo(true, target);
    }

    LOG.warn(new RepoStateException("Invalid format of the .git/HEAD file: [" + headContent + "]")); // including "refs/tags/v1"
    return HeadInfo.UNKNOWN;
  }

  /**
   * Container to hold two information items: refname from .git/HEAD and is Git on branch.
   */
  private static class HeadInfo {
    private final @Nullable String content;
    private final boolean isBranch;

    public static final HeadInfo UNKNOWN = new HeadInfo(false, null);

    HeadInfo(boolean branch, @Nullable String content) {
      isBranch = branch;
      this.content = content;
    }
  }

  private record GitBranches(@NotNull Map<GitLocalBranch, Hash> localBranches,
                             @NotNull Map<GitRemoteBranch, Hash> remoteBranches) {
  }
}
