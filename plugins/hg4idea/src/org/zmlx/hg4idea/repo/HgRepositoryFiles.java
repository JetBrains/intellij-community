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
package org.zmlx.hg4idea.repo;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;


/**
 * Stores paths to Hg service files (from .hg/ directory) that are used by IDEA, and provides methods to check if a file
 * matches once of them.
 *
 * @author Nadya Zabrodina
 */
public class HgRepositoryFiles {

  public static final String BRANCHHEADS = "cache/branchheads";  // can be branchheads or branchheads-served after approx 2.5,
  // so check for starting branchheads
  public static final String BRANCHEADSDIR = "cache";
  public static final String MERGE = "merge";
  public static final String BRANCH = "branch";
  public static final String BOOKMARKS = "bookmarks";
  public static final String LOCAL_TAGS = "localtags";
  public static final String TAGS = ".hgtags";
  public static final String CURRENT_BOOKMARK = "bookmarks.current";
  public static final String CONFIG_HGRC = "hgrc";


  @NotNull private final String myBranchHeadsPath;
  @NotNull private final String myBranchHeadsDirPath;
  @NotNull private final String myMergePath;
  @NotNull private final String myBranchPath;
  @NotNull private final String myBookmarksPath;
  @NotNull private final String myTagsPath;
  @NotNull private final String myLocalTagsPath;
  @NotNull private final String myCurrentBookmarkPath;
  @NotNull private final String myConfigHgrcPath;

  @NotNull
  public static HgRepositoryFiles getInstance(@NotNull VirtualFile hgDir) {
    return new HgRepositoryFiles(hgDir);
  }

  private HgRepositoryFiles(@NotNull VirtualFile hgDir) {
    myBranchHeadsPath = hgDir.getPath() + slash(BRANCHHEADS);
    myBranchHeadsDirPath = hgDir.getPath() + slash(BRANCHEADSDIR);
    myBranchPath = hgDir.getPath() + slash(BRANCH);
    myMergePath = hgDir.getPath() + slash(MERGE);
    myBookmarksPath = hgDir.getPath() + slash(BOOKMARKS);
    myTagsPath = hgDir.getParent().getPath() + slash(TAGS);
    myLocalTagsPath = hgDir.getPath() + slash(LOCAL_TAGS);
    myCurrentBookmarkPath = hgDir.getPath() + slash(CURRENT_BOOKMARK);
    myConfigHgrcPath = hgDir.getPath() + slash(CONFIG_HGRC);
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

  public boolean isbranchHeadsFile(String filePath) {
    return filePath.startsWith(myBranchHeadsPath);
  }

  public boolean isBranchFile(String filePath) {
    return filePath.equals(myBranchPath);
  }

  public boolean isMergeFile(String filePath) {
    return filePath.startsWith(myMergePath);
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
}
