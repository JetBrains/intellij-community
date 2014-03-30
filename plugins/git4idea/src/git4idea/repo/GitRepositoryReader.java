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

import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information about the Git repository from Git service files located in the {@code .git} folder.
 * NB: works with {@link java.io.File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Git file format.
 *
 * @author Kirill Likhodedov
 */
class GitRepositoryReader {

  private static final Logger LOG = Logger.getInstance(GitRepositoryReader.class);

  private static Pattern BRANCH_PATTERN          = Pattern.compile("ref: refs/heads/(\\S+)"); // branch reference in .git/HEAD
  // this format shouldn't appear, but we don't want to fail because of a space
  private static Pattern BRANCH_WEAK_PATTERN     = Pattern.compile(" *(ref:)? */?refs/heads/(\\S+)");
  private static Pattern COMMIT_PATTERN          = Pattern.compile("[0-9a-fA-F]+"); // commit hash

  @NonNls private static final String REFS_HEADS_PREFIX = "refs/heads/";
  @NonNls private static final String REFS_REMOTES_PREFIX = "refs/remotes/";

  @NotNull private final File          myGitDir;         // .git/
  @NotNull private final File          myHeadFile;       // .git/HEAD
  @NotNull private final File          myRefsHeadsDir;   // .git/refs/heads/
  @NotNull private final File          myRefsRemotesDir; // .git/refs/remotes/
  @NotNull private final File          myPackedRefsFile; // .git/packed-refs

  GitRepositoryReader(@NotNull File gitDir) {
    myGitDir = gitDir;
    RepositoryUtil.assertFileExists(myGitDir, ".git directory not found in " + gitDir);
    myHeadFile = new File(myGitDir, "HEAD");
    RepositoryUtil.assertFileExists(myHeadFile, ".git/HEAD file not found in " + gitDir);
    myRefsHeadsDir = new File(new File(myGitDir, "refs"), "heads");
    myRefsRemotesDir = new File(new File(myGitDir, "refs"), "remotes");
    myPackedRefsFile = new File(myGitDir, "packed-refs");
  }

  @Nullable
  private static Hash createHash(@Nullable String hash) {
    try {
      return hash == null ? GitBranch.DUMMY_HASH : HashImpl.build(hash);
    }
    catch (Throwable t) {
      LOG.info(t);
      return null;
    }
  }

  @NotNull
  public Repository.State readState() {
    if (isMergeInProgress()) {
      return Repository.State.MERGING;
    }
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
    Head head = readHead();
    if (!head.isBranch) {
      return Repository.State.DETACHED;
    }
    return Repository.State.NORMAL;
  }

  /**
   * Finds current revision value.
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  String readCurrentRevision() {
    final Head head = readHead();
    if (!head.isBranch) { // .git/HEAD is a commit
      return head.ref;
    }

    // look in /refs/heads/<branch name>
    File branchFile = null;
    for (Map.Entry<String, File> entry : readLocalBranches().entrySet()) {
      if (entry.getKey().equals(head.ref)) {
        branchFile = entry.getValue();
      }
    }
    if (branchFile != null) {
      return readBranchFile(branchFile);
    }

    // finally look in packed-refs
    return findBranchRevisionInPackedRefs(head.ref);
  }

  /**
   * If the repository is on branch, returns the current branch
   * If the repository is being rebased, returns the branch being rebased.
   * In other cases of the detached HEAD returns {@code null}.
   */
  @Nullable
  GitLocalBranch readCurrentBranch() {
    Head head = readHead();
    if (head.isBranch) {
      String branchName = head.ref;
      String hash = readCurrentRevision();  // TODO we know the branch name, so no need to read head twice
      Hash h = createHash(hash);
      if (h == null) {
        return null;
      }
      return new GitLocalBranch(branchName, h);
    }
    if (isRebaseInProgress()) {
      GitLocalBranch branch = readRebaseBranch("rebase-apply");
      if (branch == null) {
        branch = readRebaseBranch("rebase-merge");
      }
      return branch;
    }
    return null;
  }

  /**
   * Reads {@code .git/rebase-apply/head-name} or {@code .git/rebase-merge/head-name} to find out the branch which is currently being rebased,
   * and returns the {@link GitBranch} for the branch name written there, or null if these files don't exist.
   */
  @Nullable
  private GitLocalBranch readRebaseBranch(@NonNls String rebaseDirName) {
    File rebaseDir = new File(myGitDir, rebaseDirName);
    if (!rebaseDir.exists()) {
      return null;
    }
    File headName = new File(rebaseDir, "head-name");
    if (!headName.exists()) {
      return null;
    }
    String branchName = RepositoryUtil.tryLoadFile(headName);
    File branchFile = findBranchFile(branchName);
    if (!branchFile.exists()) { // can happen when rebasing from detached HEAD: IDEA-93806
      return null;
    }
    Hash hash = createHash(readBranchFile(branchFile));
    if (hash == null) {
      return null;
    }
    if (branchName.startsWith(REFS_HEADS_PREFIX)) {
      branchName = branchName.substring(REFS_HEADS_PREFIX.length());
    }
    return new GitLocalBranch(branchName, hash);
  }

  @NotNull
  private File findBranchFile(@NotNull String branchName) {
    return new File(myGitDir.getPath() + File.separator + branchName);
  }

  private boolean isMergeInProgress() {
    File mergeHead = new File(myGitDir, "MERGE_HEAD");
    return mergeHead.exists();
  }

  private boolean isRebaseInProgress() {
    File f = new File(myGitDir, "rebase-apply");
    if (f.exists()) {
      return true;
    }
    f = new File(myGitDir, "rebase-merge");
    return f.exists();
  }

  /**
   * Reads the {@code .git/packed-refs} file and tries to find the revision hash for the given reference (branch actually).
   * @param ref short name of the reference to find. For example, {@code master}.
   * @return commit hash, or {@code null} if the given ref wasn't found in {@code packed-refs}
   */
  @Nullable
  private String findBranchRevisionInPackedRefs(final String ref) {
    if (!myPackedRefsFile.exists()) {
      return null;
    }

    final AtomicReference<String> hashRef = new AtomicReference<String>();
    readPackedRefsFile(new PackedRefsLineResultHandler() {
      @Override
      public void handleResult(String hash, String branchName) {
        if (hash == null || branchName == null) {
          return;
        }
        if (branchName.endsWith(ref)) {
          hashRef.set(shortBuffer(hash));
          stop();
        }
      }
    });
    if (hashRef.get() != null) {
      return hashRef.get();
    }

    return null;
  }

  private void readPackedRefsFile(@NotNull final PackedRefsLineResultHandler handler) {
    RepositoryUtil.tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        BufferedReader reader = null;
        try {
          reader = new BufferedReader(new FileReader(myPackedRefsFile));
          String line;
          while ((line = reader.readLine()) != null) {
            parsePackedRefsLine(line, handler);
            if (handler.stopped()) {
              return null;
            }
          }
        }
        finally {
          if (reader != null) {
            reader.close();
          }
        }
        return null;
      }
    }, myPackedRefsFile);
  }

  /**
   * @return the list of local branches in this Git repository.
   *         key is the branch name, value is the file.
   */
  private Map<String, File> readLocalBranches() {
    final Map<String, File> branches = new HashMap<String, File>();
    if (!myRefsHeadsDir.exists()) {
      return branches;
    }
    FileUtil.processFilesRecursively(myRefsHeadsDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isDirectory()) {
          String relativePath = FileUtil.getRelativePath(myRefsHeadsDir, file);
          if (relativePath != null) {
            branches.put(FileUtil.toSystemIndependentName(relativePath), file);
          }
        }
        return true;
      }
    });
    return branches;
  }

  /**
   * @return all branches in this repository. local/remote/active information is stored in branch objects themselves.
   * @param remotes
   */
  GitBranchesCollection readBranches(@NotNull Collection<GitRemote> remotes) {
    Set<GitLocalBranch> localBranches = readUnpackedLocalBranches();
    Set<GitRemoteBranch> remoteBranches = readUnpackedRemoteBranches(remotes);
    GitBranchesCollection packedBranches = readPackedBranches(remotes);
    localBranches.addAll(packedBranches.getLocalBranches());
    remoteBranches.addAll(packedBranches.getRemoteBranches());
    return new GitBranchesCollection(localBranches, remoteBranches);
  }

  /**
   * @return list of branches from refs/heads. active branch is not marked as active - the caller should do this.
   */
  @NotNull
  private Set<GitLocalBranch> readUnpackedLocalBranches() {
    Set<GitLocalBranch> branches = new HashSet<GitLocalBranch>();
    for (Map.Entry<String, File> entry : readLocalBranches().entrySet()) {
      String branchName = entry.getKey();
      File branchFile = entry.getValue();
      String hash = loadHashFromBranchFile(branchFile);
      Hash h = createHash(hash);
      if (h != null) {
        branches.add(new GitLocalBranch(branchName, h));
      }
    }
    return branches;
  }

  @Nullable
  private static String loadHashFromBranchFile(@NotNull File branchFile) {
    try {
      return RepositoryUtil.tryLoadFile(branchFile);
    }
    catch (RepoStateException e) {  // notify about error but don't break the process
      LOG.error("Couldn't read " + branchFile, e);
    }
    return null;
  }

  /**
   * @return list of branches from refs/remotes.
   * @param remotes
   */
  @NotNull
  private Set<GitRemoteBranch> readUnpackedRemoteBranches(@NotNull final Collection<GitRemote> remotes) {
    final Set<GitRemoteBranch> branches = new HashSet<GitRemoteBranch>();
    if (!myRefsRemotesDir.exists()) {
      return branches;
    }
    FileUtil.processFilesRecursively(myRefsRemotesDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isDirectory() && !file.getName().equalsIgnoreCase(GitRepositoryFiles.HEAD)) {
          final String relativePath = FileUtil.getRelativePath(myGitDir, file);
          if (relativePath != null) {
            String branchName = FileUtil.toSystemIndependentName(relativePath);
            String hash = loadHashFromBranchFile(file);
            Hash h = createHash(hash);
            if (h != null) {
              GitRemoteBranch remoteBranch = GitBranchUtil.parseRemoteBranch(branchName, h, remotes);
              if (remoteBranch != null) {
                branches.add(remoteBranch);
              }
            }
          }
        }
        return true;
      }
    });
    return branches;
  }

  /**
   * @return list of local and remote branches from packed-refs. Active branch is not marked as active.
   * @param remotes
   */
  @NotNull
  private GitBranchesCollection readPackedBranches(@NotNull final Collection<GitRemote> remotes) {
    final Set<GitLocalBranch> localBranches = new HashSet<GitLocalBranch>();
    final Set<GitRemoteBranch> remoteBranches = new HashSet<GitRemoteBranch>();
    if (!myPackedRefsFile.exists()) {
      return GitBranchesCollection.EMPTY;
    }

    readPackedRefsFile(new PackedRefsLineResultHandler() {
      @Override public void handleResult(@Nullable String hashString, @Nullable String branchName) {
        if (hashString == null || branchName == null) {
          return;
        }
        hashString = shortBuffer(hashString);
        Hash hash = createHash(hashString);
        if (hash == null) {
          return;
        }
        if (branchName.startsWith(REFS_HEADS_PREFIX)) {
          localBranches.add(new GitLocalBranch(branchName, hash));
        }
        else if (branchName.startsWith(REFS_REMOTES_PREFIX)) {
          GitRemoteBranch remoteBranch = GitBranchUtil.parseRemoteBranch(branchName, hash, remotes);
          if (remoteBranch != null) {
            remoteBranches.add(remoteBranch);
          }
        }
      }
    });
    return new GitBranchesCollection(localBranches, remoteBranches);
  }

  @NotNull
  private static String readBranchFile(@NotNull File branchFile) {
    return RepositoryUtil.tryLoadFile(branchFile);
  }

  @NotNull
  private Head readHead() {
    String headContent = RepositoryUtil.tryLoadFile(myHeadFile);
    Matcher matcher = BRANCH_PATTERN.matcher(headContent);
    if (matcher.matches()) {
      return new Head(true, matcher.group(1));
    }

    if (COMMIT_PATTERN.matcher(headContent).matches()) {
      return new Head(false, headContent);
    }
    matcher = BRANCH_WEAK_PATTERN.matcher(headContent);
    if (matcher.matches()) {
      LOG.info(".git/HEAD has not standard format: [" + headContent + "]. We've parsed branch [" + matcher.group(1) + "]");
      return new Head(true, matcher.group(1));
    }
    throw new RepoStateException("Invalid format of the .git/HEAD file: \n" + headContent);
  }

  /**
   * Parses a line from the .git/packed-refs file.
   * Passes the parsed hash-branch pair to the resultHandler.
   * Comments, tags and incorrectly formatted lines are ignored, and (null, null) is passed to the handler then.
   * Using a special handler may seem to be an overhead, but it is to avoid code duplication in two methods that parse packed-refs.
   */
  private static void parsePackedRefsLine(@NotNull String line, @NotNull PackedRefsLineResultHandler resultHandler) {
    try {
      line = line.trim();
      char firstChar = line.isEmpty() ? 0 : line.charAt(0);
      if (firstChar == '#') { // ignoring comments
        return;
      }
      if (firstChar == '^') {
        // ignoring the hash which an annotated tag above points to
        return;
      }
      String hash = null;
      int i;
      for (i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (!Character.isLetterOrDigit(c)) {
          hash = line.substring(0, i);
          break;
        }
      }
      String branch = null;
      int start = i;
      if (hash != null && start < line.length() && line.charAt(start++) == ' ') {
        for (i = start; i < line.length(); i++) {
          char c = line.charAt(i);
          if (Character.isWhitespace(c)) {
            break;
          }
        }
        branch = line.substring(start, i);
      }

      if (hash != null && branch != null) {
        resultHandler.handleResult(hash.trim(), branch);
      }
      else {
        LOG.info("Ignoring invalid packed-refs line: [" + line + "]");
      }
    }
    finally {
      resultHandler.handleResult(null, null);
    }
  }

  @NotNull
  private static String shortBuffer(String raw) {
    return new String(raw);
  }

  private abstract static class PackedRefsLineResultHandler {
    private boolean myStopped;

    abstract void handleResult(@Nullable String hash, @Nullable String branchName);

    /**
     * Call this to stop further lines reading.
     */
    final void stop() {
      myStopped = true;
    }

    final boolean stopped() {
      return myStopped;
    }
  }

  /**
   * Container to hold two information items: current .git/HEAD value and is Git on branch.
   */
  private static class Head {
    @NotNull private final String ref;
    private final boolean isBranch;

    Head(boolean branch, @NotNull String ref) {
      isBranch = branch;
      this.ref = ref;
    }
  }


}
