/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;
import static git4idea.GitReference.BRANCH_NAME_HASHING_STRATEGY;
import static git4idea.repo.GitRefUtil.*;
import static java.util.Collections.emptyList;

/**
 * <p>Reads information about the Git repository from Git service files located in the {@code .git} folder.</p>
 * <p>NB: works with {@link File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Git file format.</p>
 */
class GitRepositoryReader {

  private static final Logger LOG = Logger.getInstance(GitRepositoryReader.class);

  @NotNull private final File          myHeadFile;       // .git/HEAD
  @NotNull private final File          myRefsHeadsDir;   // .git/refs/heads/
  @NotNull private final File          myRefsRemotesDir; // .git/refs/remotes/
  @NotNull private final File          myPackedRefsFile; // .git/packed-refs
  @NotNull private final GitRepositoryFiles myGitFiles;

  GitRepositoryReader(@NotNull GitRepositoryFiles gitFiles) {
    myGitFiles = gitFiles;
    myHeadFile = gitFiles.getHeadFile();
    DvcsUtil.assertFileExists(myHeadFile, ".git/HEAD file not found at " + myHeadFile);
    myRefsHeadsDir = gitFiles.getRefsHeadsFile();
    myRefsRemotesDir = gitFiles.getRefsRemotesFile();
    myPackedRefsFile = gitFiles.getPackedRefsPath();
  }

  @NotNull
  GitBranchState readState(@NotNull Collection<GitRemote> remotes) {
    Pair<Map<GitLocalBranch, Hash>, Map<GitRemoteBranch, Hash>> branches = readBranches(remotes);
    Map<GitLocalBranch, Hash> localBranches = branches.first;

    HeadInfo headInfo = readHead();
    Repository.State state = readRepositoryState(headInfo);

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
      LOG.error("Couldn't identify neither current branch nor current revision. .git/HEAD content: [" + headInfo.content + "]");
    }
    return new GitBranchState(currentRevision, currentBranch, state, localBranches, branches.second);
  }

  @NotNull
  GitHooksInfo readHooksInfo() {
    return new GitHooksInfo(isExistingExecutableFile(myGitFiles.getPreCommitHookFile()),
                            isExistingExecutableFile(myGitFiles.getPrePushHookFile()));
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

  @Nullable
  private static String getCurrentRevision(@NotNull HeadInfo headInfo, @Nullable Hash currentBranchHash) {
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

  @Nullable
  private GitLocalBranch findCurrentBranch(@NotNull HeadInfo headInfo,
                                           @NotNull Repository.State state,
                                           @NotNull Set<GitLocalBranch> localBranches) {
    final String currentBranchName = findCurrentBranchName(state, headInfo);
    if (currentBranchName == null) {
      return null;
    }
    return ContainerUtil.find(localBranches, branch -> BRANCH_NAME_HASHING_STRATEGY.equals(branch.getFullName(), currentBranchName));
  }

  @NotNull
  private Repository.State readRepositoryState(@NotNull HeadInfo headInfo) {
    if (isMergeInProgress()) {
      return Repository.State.MERGING;
    }
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
    if (!headInfo.isBranch) {
      return Repository.State.DETACHED;
    }
    return Repository.State.NORMAL;
  }

  @Nullable
  private String findCurrentBranchName(@NotNull Repository.State state, @NotNull HeadInfo headInfo) {
    String currentBranch = null;
    if (headInfo.isBranch) {
      currentBranch = headInfo.content;
    }
    else if (state == Repository.State.REBASING) {
      currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseApplyDir());
      if (currentBranch == null) {
        currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseMergeDir());
      }
    }
    return addRefsHeadsPrefixIfNeeded(currentBranch);
  }

  @Nullable
  private static String readRebaseDirBranchFile(@NonNls File rebaseDir) {
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

  @NotNull
  private Map<String, String> readPackedBranches() {
    if (!myPackedRefsFile.exists()) {
      return Collections.emptyMap();
    }
    try {
      String content = DvcsUtil.tryLoadFile(myPackedRefsFile, CharsetToolkit.UTF8);
      return ContainerUtil.map2MapNotNull(LineTokenizer.tokenize(content, false), GitRefUtil::parseRefsLine);
    }
    catch (RepoStateException e) {
      return Collections.emptyMap();
    }
  }

  @NotNull
  private Pair<Map<GitLocalBranch, Hash>, Map<GitRemoteBranch, Hash>> readBranches(@NotNull Collection<GitRemote> remotes) {
    Map<String, String> data = readBranchRefsFromFiles();
    Map<String, Hash> resolvedRefs = resolveRefs(data);
    return createBranchesFromData(remotes, resolvedRefs);
  }

  @NotNull
  private Map<String, String> readBranchRefsFromFiles() {
    Map<String, String> result = ContainerUtil.newHashMap(readPackedBranches()); // reading from packed-refs first to overwrite values by values from unpacked refs
    result.putAll(readFromBranchFiles(myRefsHeadsDir, REFS_HEADS_PREFIX));
    result.putAll(readFromBranchFiles(myRefsRemotesDir, REFS_REMOTES_PREFIX));
    result.remove(REFS_REMOTES_PREFIX + GitUtil.ORIGIN_HEAD);
    return result;
  }

  @NotNull
  private static Pair<Map<GitLocalBranch, Hash>, Map<GitRemoteBranch, Hash>> createBranchesFromData(@NotNull Collection<GitRemote> remotes,
                                                                                                    @NotNull Map<String, Hash> data) {
    Map<GitLocalBranch, Hash> localBranches = ContainerUtil.newHashMap();
    Map<GitRemoteBranch, Hash> remoteBranches = ContainerUtil.newHashMap();
    for (Map.Entry<String, Hash> entry : data.entrySet()) {
      String refName = entry.getKey();
      Hash hash = entry.getValue();
      if (refName.startsWith(REFS_HEADS_PREFIX)) {
        localBranches.put(new GitLocalBranch(refName), hash);
      }
      else if (refName.startsWith(REFS_REMOTES_PREFIX)) {
        remoteBranches.put(parseRemoteBranch(refName, remotes), hash);
      }
      else {
        LOG.warn("Unexpected ref format: " + refName);
      }
    }
    return Pair.create(localBranches, remoteBranches);
  }

  @Nullable
  private static String loadHashFromBranchFile(@NotNull File branchFile) {
    return DvcsUtil.tryLoadFileOrReturn(branchFile, null);
  }

  @NotNull
  private static Map<String, String> readFromBranchFiles(@NotNull final File refsRootDir, @NotNull final String prefix) {
    if (!refsRootDir.exists()) {
      return Collections.emptyMap();
    }
    final Map<String, String> result = new HashMap<>();
    FileUtil.processFilesRecursively(refsRootDir,  file-> {
        if (!file.isDirectory() && !isHidden(file)) {
          String relativePath = FileUtil.getRelativePath(refsRootDir, file);
          if (relativePath != null) {
            String branchName = prefix + FileUtil.toSystemIndependentName(relativePath);
            boolean isBranchNameValid = GitRefNameValidator.getInstance().checkInput(branchName);
            if (isBranchNameValid) {String hash = loadHashFromBranchFile(file);
            if (hash != null) {
              result.put(branchName, hash);}
            }
          }
        }
        return true;
      }
    , dir -> !isHidden(dir));
    return result;
  }

  private static boolean isHidden(@NotNull File file) {
    return file.getName().startsWith(".");
  }

  @NotNull
  private static GitRemoteBranch parseRemoteBranch(@NotNull String fullBranchName,
                                                   @NotNull Collection<GitRemote> remotes) {
    String stdName = GitBranchUtil.stripRefsPrefix(fullBranchName);

    int slash = stdName.indexOf('/');
    if (slash == -1) { // .git/refs/remotes/my_branch => git-svn
      return new GitSvnRemoteBranch(fullBranchName);
    }
    else {
      GitRemote remote;
      String remoteName;
      String branchName;
      do {
        remoteName = stdName.substring(0, slash);
        branchName = stdName.substring(slash + 1);
        remote = GitUtil.findRemoteByName(remotes, remoteName);
        slash = stdName.indexOf('/', slash + 1);
      } while(remote == null && slash >= 0);

      if (remote == null) {
        // user may remove the remote section from .git/config, but leave remote refs untouched in .git/refs/remotes
        LOG.trace(String.format("No remote found with the name [%s]. All remotes: %s", remoteName, remotes));
        GitRemote fakeRemote = new GitRemote(remoteName, emptyList(), emptyList(), emptyList(), emptyList());
        return new GitStandardRemoteBranch(fakeRemote, branchName);
      }
      return new GitStandardRemoteBranch(remote, branchName);
    }
  }

  @NotNull
  private HeadInfo readHead() {
    String headContent;
    try {
      headContent = DvcsUtil.tryLoadFile(myHeadFile, CharsetToolkit.UTF8);
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return new HeadInfo(false, null);
    }

    Hash hash = parseHash(headContent);
    if (hash != null) {
      return new HeadInfo(false, headContent);
    }
    String target = getTarget(headContent);
    if (target != null) {
      return new HeadInfo(true, target);
    }
    LOG.error(new RepoStateException("Invalid format of the .git/HEAD file: [" + headContent + "]")); // including "refs/tags/v1"
    return new HeadInfo(false, null);
  }

  /**
   * Container to hold two information items: current .git/HEAD value and is Git on branch.
   */
  private static class HeadInfo {
    @Nullable private final String content;
    private final boolean isBranch;

    HeadInfo(boolean branch, @Nullable String content) {
      isBranch = branch;
      this.content = content;
    }
  }
}
