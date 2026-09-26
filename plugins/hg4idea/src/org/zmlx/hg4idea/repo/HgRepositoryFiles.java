// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.repo;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;


/**
 * Stores paths to Hg service files that are used by IDEA, and provides methods to check if a file
 * matches once of them.
 *
 */
public final class HgRepositoryFiles {

  private static final @NonNls String BRANCHHEADS = "cache/branch";//branchheads <2.5; branchheads-served >= 2.5 and <2.9; branch2-served >=2.9
  // so check for starting branch
  private static final @NonNls String BRANCHEADSDIR = "cache";
  private static final @NonNls String MERGE = "merge";
  private static final @NonNls String REBASE = "rebase"; //rebasestate
  private static final @NonNls String BRANCH = "branch";
  private static final @NonNls String DIRSTATE = "dirstate";
  private static final @NonNls String BOOKMARKS = "bookmarks";
  private static final @NonNls String LOCAL_TAGS = "localtags";
  private static final @NonNls String TAGS = ".hgtags";
  private static final @NonNls String CURRENT_BOOKMARK = "bookmarks.current";
  private static final @NonNls String MQDIR = "patches";
  private static final @NonNls String CONFIG_HGRC = "hgrc";
  public static final @NonNls String HGIGNORE = ".hgignore";


  private final @NotNull String myBranchHeadsPath;
  private final @NotNull String myBranchHeadsDirPath;
  private final @NotNull String myMergePath;
  private final @NotNull String myRebasePath;
  private final @NotNull String myBranchPath;
  private final @NotNull String myDirstatePath;
  private final @NotNull String myBookmarksPath;
  private final @NotNull String myTagsPath;
  private final @NotNull String myLocalTagsPath;
  private final @NotNull String myCurrentBookmarkPath;
  private final @NotNull String myMQDirPath;
  private final @NotNull String myConfigHgrcPath;
  private final @NotNull String myHgIgnorePath;

  public static @NotNull HgRepositoryFiles getInstance(@NotNull VirtualFile hgDir) {
    return new HgRepositoryFiles(hgDir);
  }

  private HgRepositoryFiles(@NotNull VirtualFile hgDir) {
    myBranchHeadsPath = hgDir.getPath() + slash(BRANCHHEADS);
    myBranchHeadsDirPath = hgDir.getPath() + slash(BRANCHEADSDIR);
    myBranchPath = hgDir.getPath() + slash(BRANCH);
    myDirstatePath = hgDir.getPath() + slash(DIRSTATE);
    myMergePath = hgDir.getPath() + slash(MERGE);
    myRebasePath = hgDir.getPath() + slash(REBASE);
    myBookmarksPath = hgDir.getPath() + slash(BOOKMARKS);
    VirtualFile repoDir = hgDir.getParent();
    myTagsPath = repoDir.getPath() + slash(TAGS);
    myLocalTagsPath = hgDir.getPath() + slash(LOCAL_TAGS);
    myCurrentBookmarkPath = hgDir.getPath() + slash(CURRENT_BOOKMARK);
    myMQDirPath = hgDir.getPath() + slash(MQDIR);
    myConfigHgrcPath = hgDir.getPath() + slash(CONFIG_HGRC);
    myHgIgnorePath = repoDir.getPath() + slash(HGIGNORE);
  }

  private static @NotNull String slash(@NotNull String s) {
    return "/" + s;
  }

  /**
   * Returns subdirectories of .hg which we are interested in - they should be watched by VFS.
   */
  static @NotNull Collection<String> getSubDirRelativePaths() {
    return Arrays.asList(slash(BRANCHHEADS), slash(MERGE));
  }

  public @NotNull String getBranchHeadsDirPath() {
    return myBranchHeadsDirPath;
  }

  public @NotNull String getMQDirPath() {
    return myMQDirPath;
  }

  public boolean isbranchHeadsFile(String filePath) {
    return filePath.startsWith(myBranchHeadsPath);
  }

  public boolean isBranchFile(String filePath) {
    return filePath.equals(myBranchPath);
  }

  public boolean isDirstateFile(String filePath) {
    return filePath.equals(myDirstatePath);
  }

  public boolean isMergeFile(String filePath) {
    return filePath.startsWith(myMergePath);
  }

  public boolean isRebaseFile(String filePath) {
    return filePath.startsWith(myRebasePath);
  }

  public boolean isBookmarksFile(String filePath) {
    return filePath.equals(myBookmarksPath);
  }

  public boolean isCurrentBookmarksFile(String filePath) {
    return filePath.equals(myCurrentBookmarkPath);
  }

  public boolean isConfigHgrcFile(String filePath) {
    return filePath.equals(myConfigHgrcPath);
  }

  public boolean isTagsFile(String filePath) {
    return filePath.equals(myTagsPath);
  }

  public boolean isLocalTagsFile(String filePath) {
    return filePath.equals(myLocalTagsPath);
  }

  public boolean isMqFile(String filePath) {
    return filePath.startsWith(myMQDirPath);
  }

  public boolean isHgIgnore(String filePath) {
    return filePath.equals(myHgIgnorePath);
  }
}
