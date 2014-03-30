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

import com.intellij.openapi.vfs.VirtualFile;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

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
  public static final String REBASE_APPLY = "rebase-apply";
  public static final String REBASE_MERGE = "rebase-merge";
  public static final String PACKED_REFS = "packed-refs";
  public static final String REFS_HEADS = "refs/heads";
  public static final String REFS_REMOTES = "refs/remotes";
  public static final String SQUASH_MSG = "SQUASH_MSG";

  public static final String GIT_HEAD  = DOT_GIT + slash(HEAD);
  public static final String GIT_REFS_REMOTES = DOT_GIT + slash(REFS_REMOTES);
  public static final String GIT_PACKED_REFS = DOT_GIT + slash(PACKED_REFS);
  public static final String GIT_MERGE_HEAD = DOT_GIT + slash(MERGE_HEAD);
  public static final String GIT_MERGE_MSG = DOT_GIT + slash(MERGE_MSG);
  public static final String GIT_SQUASH_MSG = DOT_GIT + slash(SQUASH_MSG);
  public static final String GIT_COMMIT_EDITMSG = DOT_GIT + slash(COMMIT_EDITMSG);

  private final String myConfigFilePath;
  private final String myHeadFilePath;
  private final String myIndexFilePath;
  private final String myMergeHeadPath;
  private final String myRebaseApplyPath;
  private final String myRebaseMergePath;
  private final String myPackedRefsPath;
  private final String myRefsHeadsDirPath;
  private final String myRefsRemotesDirPath;
  private final String myCommitMessagePath;
  private final String myExcludePath;

  public static GitRepositoryFiles getInstance(@NotNull VirtualFile gitDir) {
    // maybe will be cached later to store a single GitRepositoryFiles for a root. 
    return new GitRepositoryFiles(gitDir);
  }

  private GitRepositoryFiles(@NotNull VirtualFile gitDir) {
    // add .git/ and .git/refs/heads to the VFS
    // save paths of the files, that we will watch
    String gitDirPath = GitFileUtils.stripFileProtocolPrefix(gitDir.getPath());
    myConfigFilePath = gitDirPath + slash(CONFIG);
    myHeadFilePath = gitDirPath + slash(HEAD);
    myIndexFilePath = gitDirPath + slash(INDEX);
    myMergeHeadPath = gitDirPath + slash(MERGE_HEAD);
    myCommitMessagePath = gitDirPath + slash(COMMIT_EDITMSG);
    myRebaseApplyPath = gitDirPath + slash(REBASE_APPLY);
    myRebaseMergePath = gitDirPath + slash(REBASE_MERGE);
    myPackedRefsPath = gitDirPath + slash(PACKED_REFS);
    myRefsHeadsDirPath = gitDirPath + slash(REFS_HEADS);
    myRefsRemotesDirPath = gitDirPath + slash(REFS_REMOTES);
    myExcludePath = gitDirPath + slash(INFO_EXCLUDE);
  }

  @NotNull
  private static String slash(@NotNull String s) {
    return "/" + s;
  }

  /**
   * Returns subdirectories of .git which we are interested in - they should be watched by VFS.
   */
  @NotNull
  static Collection<String> getSubDirRelativePaths() {
    return Arrays.asList(slash(REFS_HEADS), slash(REFS_REMOTES), slash(INFO));
  }
  
  @NotNull
  String getRefsHeadsPath() {
    return myRefsHeadsDirPath;
  }
  
  @NotNull
  String getRefsRemotesPath() {
    return myRefsRemotesDirPath;
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
   * Any file in .git/refs/heads, i.e. a branch reference file.
   */
  public boolean isBranchFile(String filePath) {
    return filePath.startsWith(myRefsHeadsDirPath);
  }

  /**
   * Any file in .git/refs/remotes, i.e. a remote branch reference file.
   */
  public boolean isRemoteBranchFile(String filePath) {
    return filePath.startsWith(myRefsRemotesDirPath);
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
