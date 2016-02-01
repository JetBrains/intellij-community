/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import static com.intellij.psi.impl.SyntheticFileSystemItem.LOG;
import static git4idea.GitUtil.DOT_GIT;

/**
 * Stores paths to Git service files (from .git/ directory) that are used by IDEA, and provides test-methods to check if a file
 * matches once of them.
 *
 * @author Kirill Likhodedov
 */
public class GitRepositoryFiles {

  public static final String COMMIT_EDITMSG = "COMMIT_EDITMSG";
  public static final String CONFIG = "config";
  public static final String HEAD = "HEAD";
  public static final String INDEX = "index";
  public static final String INFO = "info";
  public static final String INFO_EXCLUDE = INFO + "/exclude";
  public static final String MERGE_HEAD = "MERGE_HEAD";
  public static final String MERGE_MSG = "MERGE_MSG";
  public static final String ORIG_HEAD = "ORIG_HEAD";
  public static final String REBASE_APPLY = "rebase-apply";
  public static final String REBASE_MERGE = "rebase-merge";
  public static final String PACKED_REFS = "packed-refs";
  public static final String REFS = "refs";
  public static final String HEADS = "heads";
  public static final String TAGS = "tags";
  public static final String REMOTES = "remotes";
  public static final String SQUASH_MSG = "SQUASH_MSG";

  public static final String GIT_HEAD  = DOT_GIT + slash(HEAD);
  public static final String GIT_MERGE_HEAD = DOT_GIT + slash(MERGE_HEAD);
  public static final String GIT_MERGE_MSG = DOT_GIT + slash(MERGE_MSG);
  public static final String GIT_SQUASH_MSG = DOT_GIT + slash(SQUASH_MSG);

  private final String myGitDirPath;
  private final String myConfigFilePath;
  private final String myHeadFilePath;
  private final String myIndexFilePath;
  private final String myMergeHeadPath;
  private final String myOrigHeadPath;
  private final String myRebaseApplyPath;
  private final String myRebaseMergePath;
  private final String myPackedRefsPath;
  private final String myRefsHeadsDirPath;
  private final String myRefsRemotesDirPath;
  private final String myRefsTagsPath;
  private final String myCommitMessagePath;
  private final String myInfoDirPath;
  private final String myExcludePath;

  private GitRepositoryFiles(@NotNull VirtualFile gitDir,
                             @NotNull File configFile,
                             @NotNull File headFile,
                             @NotNull File refsDir,
                             @NotNull File packedRefsFile) {
    myGitDirPath = GitFileUtils.stripFileProtocolPrefix(gitDir.getPath());
    myConfigFilePath = FileUtil.toSystemIndependentName(configFile.getPath());
    myHeadFilePath = FileUtil.toSystemIndependentName(headFile.getPath());
    myIndexFilePath = myGitDirPath + slash(INDEX);
    myMergeHeadPath = myGitDirPath + slash(MERGE_HEAD);
    myOrigHeadPath = myGitDirPath + slash(ORIG_HEAD);
    myCommitMessagePath = myGitDirPath + slash(COMMIT_EDITMSG);
    myRebaseApplyPath = myGitDirPath + slash(REBASE_APPLY);
    myRebaseMergePath = myGitDirPath + slash(REBASE_MERGE);
    myPackedRefsPath = FileUtil.toSystemIndependentName(packedRefsFile.getPath());
    String refsPath = FileUtil.toSystemIndependentName(refsDir.getPath());
    myRefsHeadsDirPath = refsPath + slash(HEADS);
    myRefsTagsPath = refsPath + slash(TAGS);
    myRefsRemotesDirPath = refsPath + slash(REMOTES);
    myInfoDirPath = myGitDirPath + slash(INFO);
    myExcludePath = myGitDirPath + slash(INFO_EXCLUDE);
  }

  @NotNull
  public static GitRepositoryFiles getInstance(@NotNull VirtualFile gitDir) {
    VirtualFile gitDirForWorktree = getMainGitDirForWorktree(gitDir);
    File headFile = new File(gitDir.getPath(), HEAD);
    File refsDir;
    File packedRefsFile;
    File configFile;
    if (gitDirForWorktree == null) {
      refsDir = new File(gitDir.getPath(), REFS);
      packedRefsFile = new File(gitDir.getPath(), PACKED_REFS);
      configFile = new File(gitDir.getPath(), CONFIG);
    }
    else {
      refsDir = new File(gitDirForWorktree.getPath(), REFS);
      packedRefsFile = new File(gitDirForWorktree.getPath(), PACKED_REFS);
      configFile = new File(gitDirForWorktree.getPath(), CONFIG);
    }
    return new GitRepositoryFiles(gitDir, configFile, headFile, refsDir, packedRefsFile);
  }

  /**
   * Checks if the given .git directory is actually a worktree's git directory, and returns the main .git directory if it is true.
   * If it is not a worktree, returns null.
   * <p/>
   * Worktree's ".git" file references {@code <main-project>/.git/worktrees/<worktree-name>}
   */
  @Nullable
  private static VirtualFile getMainGitDirForWorktree(@NotNull VirtualFile gitDir) {
    VirtualFile parent = gitDir.getParent();
    if (parent == null) return null;
    VirtualFile grandParent = parent.getParent();
    if (grandParent == null) return null;
    if (!gitDir.getName().equals(DOT_GIT) && parent.getName().equals("worktrees") && grandParent.getName().equals(DOT_GIT)) {
      LOG.info("git dir " + gitDir.getPath() + " is a worktree");
      return grandParent;
    }
    return null;
  }

  @NotNull
  private static String slash(@NotNull String s) {
    return "/" + s;
  }

  /**
   * Returns subdirectories of .git which we are interested in - they should be watched by VFS.
   */
  @NotNull
  Collection<String> getDirsToWatch() {
    return Arrays.asList(myRefsHeadsDirPath, myRefsRemotesDirPath, myRefsTagsPath, myInfoDirPath);
  }

  @NotNull
  String getGitDirPath() {
    return myGitDirPath;
  }

  @NotNull
  String getRefsHeadsPath() {
    return myRefsHeadsDirPath;
  }

  @NotNull
  String getRefsRemotesPath() {
    return myRefsRemotesDirPath;
  }

  @NotNull
  String getRefsTagsPath() {
    return myRefsTagsPath;
  }

  @NotNull
  public String getPackedRefsPath() {
    return myPackedRefsPath;
  }

  @NotNull
  public String getHeadPath() {
    return myHeadFilePath;
  }

  @NotNull
  public String getConfigPath() {
    return myConfigFilePath;
  }

  /**
   * {@code .git/config}
   */
  public boolean isConfigFile(String filePath) {
    return filePath.equals(myConfigFilePath);
  }

  /**
   * .git/index
   */
  public boolean isIndexFile(String filePath) {
    return filePath.equals(myIndexFilePath);
  }

  /**
   * .git/HEAD
   */
  public boolean isHeadFile(String file) {
    return file.equals(myHeadFilePath);
  }

  /**
   * .git/ORIG_HEAD
   */
  public boolean isOrigHeadFile(@NotNull String file) {
    return file.equals(myOrigHeadPath);
  }

  /**
   * Any file in .git/refs/heads, i.e. a branch reference file.
   */
  public boolean isBranchFile(String filePath) {
    return filePath.startsWith(myRefsHeadsDirPath);
  }

  /**
   * Checks if the given filePath represents the ref file of the given branch.
   *
   * @param filePath       the path to check, in system-independent format (e.g. with "/").
   * @param fullBranchName full name of a ref, e.g. {@code refs/heads/master}.
   * @return true iff the filePath represents the .git/refs/heads... file for the given branch.
   */
  public boolean isBranchFile(@NotNull String filePath, @NotNull String fullBranchName) {
    return FileUtil.pathsEqual(filePath, myGitDirPath + slash(fullBranchName));
  }

  /**
   * Any file in .git/refs/remotes, i.e. a remote branch reference file.
   */
  public boolean isRemoteBranchFile(String filePath) {
    return filePath.startsWith(myRefsRemotesDirPath);
  }

  /**
   * .git/refs/tags/*
   */
  public boolean isTagFile(@NotNull String path) {
    return path.startsWith(myRefsTagsPath);
  }

  /**
   * .git/rebase-merge or .git/rebase-apply
   */
  public boolean isRebaseFile(String path) {
    return path.equals(myRebaseApplyPath) || path.equals(myRebaseMergePath);
  }

  /**
   * .git/MERGE_HEAD
   */
  public boolean isMergeFile(String file) {
    return file.equals(myMergeHeadPath);
  }

  /**
   * .git/packed-refs
   */
  public boolean isPackedRefs(String file) {
    return file.equals(myPackedRefsPath);
  }

  public boolean isCommitMessageFile(@NotNull String file) {
    return file.equals(myCommitMessagePath);
  }

  /**
   * {@code $GIT_DIR/info/exclude}
   */
  public boolean isExclude(@NotNull String path) {
    return path.equals(myExcludePath);
  }

}
