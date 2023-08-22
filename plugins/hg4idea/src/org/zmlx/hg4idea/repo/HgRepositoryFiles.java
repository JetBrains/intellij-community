// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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


  @NotNull private final String myBranchHeadsPath;
  @NotNull private final String myBranchHeadsDirPath;
  @NotNull private final String myMergePath;
  @NotNull private final String myRebasePath;
  @NotNull private final String myBranchPath;
  @NotNull private final String myDirstatePath;
  @NotNull private final String myBookmarksPath;
  @NotNull private final String myTagsPath;
  @NotNull private final String myLocalTagsPath;
  @NotNull private final String myCurrentBookmarkPath;
  @NotNull private final String myMQDirPath;
  @NotNull private final String myConfigHgrcPath;
  @NotNull private final String myHgIgnorePath;

  @NotNull
  public static HgRepositoryFiles getInstance(@NotNull VirtualFile hgDir) {
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

  @NotNull
  private static String slash(@NotNull String s) {
    return "/" + s;
  }

  /**
   * Returns subdirectories of .hg which we are interested in - they should be watched by VFS.
   */
  @NotNull
  static Collection<String> getSubDirRelativePaths() {
    return Arrays.asList(slash(BRANCHHEADS), slash(MERGE));
  }

  @NotNull
  public String getBranchHeadsDirPath() {
    return myBranchHeadsDirPath;
  }

  @NotNull
  public String getMQDirPath() {
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
