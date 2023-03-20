// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Stores paths to Git service files that are used by IDEA, and provides test-methods to check if a file
 * matches once of them.
 */
public final class GitRepositoryFiles {
  private static final Logger LOG = Logger.getInstance(GitRepositoryFiles.class);

  public static final String GITIGNORE = ".gitignore";

  private static final @NonNls String CHERRY_PICK_HEAD = "CHERRY_PICK_HEAD";
  public static final @NonNls String COMMIT_EDITMSG = "COMMIT_EDITMSG";
  private static final @NonNls String CONFIG = "config";
  private static final @NonNls String HEAD = "HEAD";
  private static final @NonNls String INDEX = "index";
  private static final @NonNls String INFO = "info";
  private static final @NonNls String INFO_EXCLUDE = INFO + "/exclude";
  private static final @NonNls String MERGE_HEAD = "MERGE_HEAD";
  private static final @NonNls String MERGE_MSG = "MERGE_MSG";
  private static final @NonNls String ORIG_HEAD = "ORIG_HEAD";
  private static final @NonNls String REBASE_APPLY = "rebase-apply";
  private static final @NonNls String REBASE_MERGE = "rebase-merge";
  private static final @NonNls String PACKED_REFS = "packed-refs";
  private static final @NonNls String REFS = "refs";
  private static final @NonNls String REVERT_HEAD = "REVERT_HEAD";
  private static final @NonNls String HEADS = "heads";
  private static final @NonNls String TAGS = "tags";
  private static final @NonNls String REMOTES = "remotes";
  private static final @NonNls String SQUASH_MSG = "SQUASH_MSG";
  private static final @NonNls String HOOKS = "hooks";
  private static final @NonNls String PRE_COMMIT_HOOK = "pre-commit";
  private static final @NonNls String PRE_PUSH_HOOK = "pre-push";
  private static final @NonNls String COMMIT_MSG_HOOK = "commit-msg";
  private static final @NonNls String SHALLOW = "shallow";
  private static final @NonNls String LOGS = "logs";
  private static final @NonNls String STASH = "stash";
  private static final @NonNls String WORKTREES_DIR = "worktrees";

  private final VirtualFile myRootDir;
  private final VirtualFile myMainDir;
  private final VirtualFile myWorktreeDir;

  private final @NonNls String myConfigFilePath;
  private final @NonNls String myHeadFilePath;
  private final @NonNls String myIndexFilePath;
  private final @NonNls String myMergeHeadPath;
  private final @NonNls String myCherryPickHeadPath;
  private final @NonNls String myRevertHeadPath;
  private final @NonNls String myOrigHeadPath;
  private final @NonNls String myRebaseApplyPath;
  private final @NonNls String myRebaseMergePath;
  private final @NonNls String myPackedRefsPath;
  private final @NonNls String myRefsHeadsDirPath;
  private final @NonNls String myRefsRemotesDirPath;
  private final @NonNls String myRefsTagsPath;
  private final @NonNls String myCommitMessagePath;
  private final @NonNls String myMergeMessagePath;
  private final @NonNls String myMergeSquashPath;
  private final @NonNls String myInfoDirPath;
  private final @NonNls String myExcludePath;
  private final @NonNls String myHooksDirPath;
  private final @NonNls String myShallow;
  private final @NonNls String myStashReflogPath;
  private final @NonNls String myWorktreesDirPath;

  private @Nullable @NonNls String myCustomHooksDirPath;

  /**
   * @param rootDir     Root of the repository (parent directory of '.git' file/directory).
   * @param mainDir     '.git' directory location. For worktrees - location of the 'main_repo/.git'.
   * @param worktreeDir '.git' directory location. For worktrees - location of the 'main_repo/.git/worktrees/worktree_name/'.
   */
  private GitRepositoryFiles(@NotNull VirtualFile rootDir, @NotNull VirtualFile mainDir, @NotNull VirtualFile worktreeDir) {
    myRootDir = rootDir;
    myMainDir = mainDir;
    myWorktreeDir = worktreeDir;

    String mainPath = myMainDir.getPath();
    myConfigFilePath = mainPath + slash(CONFIG);
    myPackedRefsPath = mainPath + slash(PACKED_REFS);
    String refsPath = mainPath + slash(REFS);
    myRefsHeadsDirPath = refsPath + slash(HEADS);
    myRefsTagsPath = refsPath + slash(TAGS);
    myRefsRemotesDirPath = refsPath + slash(REMOTES);
    myInfoDirPath = mainPath + slash(INFO);
    myExcludePath = mainPath + slash(INFO_EXCLUDE);
    myHooksDirPath = mainPath + slash(HOOKS);
    myShallow = mainPath + slash(SHALLOW);
    myStashReflogPath = mainPath + slash(LOGS) + slash(REFS) + slash(STASH);
    myWorktreesDirPath = mainPath + slash(WORKTREES_DIR);

    String worktreePath = myWorktreeDir.getPath();
    myHeadFilePath = worktreePath + slash(HEAD);
    myIndexFilePath = worktreePath + slash(INDEX);
    myMergeHeadPath = worktreePath + slash(MERGE_HEAD);
    myCherryPickHeadPath = worktreePath + slash(CHERRY_PICK_HEAD);
    myRevertHeadPath = worktreePath + slash(REVERT_HEAD);
    myOrigHeadPath = worktreePath + slash(ORIG_HEAD);
    myCommitMessagePath = worktreePath + slash(COMMIT_EDITMSG);
    myMergeMessagePath = worktreePath + slash(MERGE_MSG);
    myMergeSquashPath = worktreePath + slash(SQUASH_MSG);
    myRebaseApplyPath = worktreePath + slash(REBASE_APPLY);
    myRebaseMergePath = worktreePath + slash(REBASE_MERGE);
  }

  @NotNull
  public static GitRepositoryFiles createInstance(@NotNull VirtualFile rootDir,
                                                  @NotNull VirtualFile gitDir) {
    VirtualFile gitDirForWorktree = getMainGitDirForWorktree(gitDir);
    VirtualFile mainDir = gitDirForWorktree == null ? gitDir : gitDirForWorktree;
    return new GitRepositoryFiles(rootDir, mainDir, gitDir);
  }

  /**
   * Checks if the given .git directory is actually a worktree's git directory, and returns the main .git directory if it is true.
   * If it is not a worktree, returns null.
   * <p/>
   * Worktree's ".git" file references {@code <main-project>/.git/worktrees/<worktree-name>}
   */
  @Nullable
  private static VirtualFile getMainGitDirForWorktree(@NotNull VirtualFile gitDir) {
    File gitDirFile = virtualToIoFile(gitDir);
    File commonDir = new File(gitDirFile, "commondir");
    if (!commonDir.exists()) return null;
    String pathToMain;
    try {
      pathToMain = FileUtil.loadFile(commonDir).trim();
    }
    catch (IOException e) {
      LOG.error("Couldn't load " + commonDir, e);
      return null;
    }
    String mainDir = FileUtil.toCanonicalPath(gitDirFile.getPath() + File.separator + pathToMain, true);
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile mainDirVF = lfs.refreshAndFindFileByPath(mainDir);
    if (mainDirVF != null) return mainDirVF;
    return lfs.refreshAndFindFileByPath(pathToMain); // absolute path is also possible
  }

  @NotNull
  private static String slash(@NotNull String s) {
    return "/" + s;
  }

  /**
   * Returns subdirectories and paths of .git which we are interested in - they should be watched by VFS.
   */
  @NotNull
  Collection<String> getPathsToWatch() {
    return Arrays.asList(myRefsHeadsDirPath, myRefsRemotesDirPath, myRefsTagsPath, myInfoDirPath, myHooksDirPath, myStashReflogPath);
  }

  @NotNull
  File getRefsHeadsFile() {
    return file(myRefsHeadsDirPath);
  }

  @NotNull
  File getRefsRemotesFile() {
    return file(myRefsRemotesDirPath);
  }

  @NotNull
  File getRefsTagsFile() {
    return file(myRefsTagsPath);
  }

  @NotNull
  File getPackedRefsPath() {
    return file(myPackedRefsPath);
  }

  @NotNull
  public File getHeadFile() {
    return file(myHeadFilePath);
  }

  @NotNull
  public File getConfigFile() {
    return file(myConfigFilePath);
  }

  @NotNull
  public File getRebaseMergeDir() {
    return file(myRebaseMergePath);
  }

  @NotNull
  public File getRebaseApplyDir() {
    return file(myRebaseApplyPath);
  }

  @NotNull
  public File getMergeHeadFile() {
    return file(myMergeHeadPath);
  }

  @NotNull
  public File getCherryPickHead() {
    return file(myCherryPickHeadPath);
  }

  @NotNull
  public File getRevertHead() {
    return file(myRevertHeadPath);
  }

  @NotNull
  public File getMergeMessageFile() {
    return file(myMergeMessagePath);
  }

  @NotNull
  public File getSquashMessageFile() {
    return file(myMergeSquashPath);
  }

  public void updateCustomPaths(@NotNull GitConfig.Core core) {
    String hooksPath = core.getHooksPath();
    if (hooksPath != null) {
      myCustomHooksDirPath = myRootDir.toNioPath().resolve(hooksPath).toString();
    }
    else {
      myCustomHooksDirPath = null;
    }
  }

  @NotNull
  public File getPreCommitHookFile() {
    return hook(PRE_COMMIT_HOOK);
  }

  @NotNull
  public File getPrePushHookFile() {
    return hook(PRE_PUSH_HOOK);
  }

  @NotNull
  public File getCommitMsgHookFile() {
    return hook(COMMIT_MSG_HOOK);
  }

  @NotNull
  public File getShallowFile() {
    return file(myShallow);
  }

  @NotNull
  public File getExcludeFile() {
    return file(myExcludePath);
  }

  @NotNull
  public File getStashReflogFile() {
    return file(myStashReflogPath);
  }

  @NotNull
  public File getWorktreesDirFile() {
    return file(myWorktreesDirPath);
  }

  @NotNull
  private File hook(@NotNull String filePath) {
    return file(ObjectUtils.chooseNotNull(myCustomHooksDirPath, myHooksDirPath) + slash(filePath));
  }

  @NotNull
  private static File file(@NotNull String filePath) {
    return new File(FileUtil.toSystemDependentName(filePath));
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
    return FileUtil.pathsEqual(filePath, myMainDir.getPath() + slash(fullBranchName));
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

  /**
   * .git/logs/refs/stash
   */
  public boolean isStashReflogFile(@NotNull String path) {
    return path.equals(myStashReflogPath);
  }

  /**
   * Refresh all .git repository files asynchronously and recursively.
   *
   * @see #refreshTagsFiles() if you need the "main" data (branches, HEAD, etc.) to be updated synchronously.
   */
  public void refresh() {
    VfsUtil.markDirtyAndRefresh(true, true, false, myMainDir, myWorktreeDir);
  }

  /**
   * Refresh .git/index asynchronously.
   */
  public void refreshIndexFile() {
    VirtualFile indexFilePath = LocalFileSystem.getInstance().refreshAndFindFileByPath(myIndexFilePath);
    VfsUtil.markDirtyAndRefresh(true, false, false, indexFilePath);
  }

  /**
   * Refresh that part of .git repository files, which is not covered by {@link GitRepository#update()}, e.g. the {@code refs/tags/} dir.
   *
   * The call to this method should be probably be done together with a call to update(): thus all information will be updated,
   * but some of it will be updated synchronously, the rest - asynchronously.
   */
  public void refreshTagsFiles() {
    VirtualFile tagsDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(myRefsTagsPath);
    VirtualFile packedRefsFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myPackedRefsPath);
    VfsUtil.markDirtyAndRefresh(true, true, false, tagsDir, packedRefsFile);
  }

  @NotNull
  Collection<VirtualFile> getRootDirs() {
    return ContainerUtil.newHashSet(myMainDir, myWorktreeDir);
  }
}
