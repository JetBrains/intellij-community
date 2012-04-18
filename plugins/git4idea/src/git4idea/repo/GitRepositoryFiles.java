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

/**
 * Stores paths to Git service files (from .git/ directory) that are used by IDEA, and provides test-methods to check if a file
 * matches once of them.
 *
 * @author Kirill Likhodedov
 */
public class GitRepositoryFiles {

  public static final String REFS_HEADS = "/refs/heads";
  public static final String REFS_REMOTES = "/refs/remotes";
  public static final String INFO = "/info";
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
    myConfigFilePath = gitDirPath + "/config";
    myHeadFilePath = gitDirPath + "/HEAD";
    myIndexFilePath = gitDirPath + "/index";
    myMergeHeadPath = gitDirPath + "/MERGE_HEAD";
    myCommitMessagePath = gitDirPath + "/COMMIT_EDITMSG";
    myRebaseApplyPath = gitDirPath + "/rebase-apply";
    myRebaseMergePath = gitDirPath + "/rebase-merge";
    myPackedRefsPath = gitDirPath + "/packed-refs";
    myRefsHeadsDirPath = gitDirPath + REFS_HEADS;
    myRefsRemotesDirPath = gitDirPath + REFS_REMOTES;
    myExcludePath = gitDirPath + INFO + "/exclude";
  }

  /**
   * Returns subdirectories of .git which we are interested in - they should be watched by VFS.
   */
  @NotNull
  static Collection<String> getSubDirRelativePaths() {
    return Arrays.asList(REFS_HEADS, REFS_REMOTES, INFO);
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
