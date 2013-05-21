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

  public static final String BRANCHHEADS = "cache/branchheads";
  public static final String MERGE = "merge";
  public static final String BRANCH = "branch";


  private final String myBranchHeadsPath;
  private final String myMergePath;
  private final String myBranchPath;


  public static HgRepositoryFiles getInstance(@NotNull VirtualFile hgDir) {
    return new HgRepositoryFiles(hgDir);
  }

  private HgRepositoryFiles(@NotNull VirtualFile hgDir) {
    myBranchHeadsPath = hgDir.getPath() + slash(BRANCHHEADS);
    myBranchPath = hgDir.getPath() + slash(BRANCH);
    myMergePath = hgDir.getPath() + slash(MERGE);
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

  public String getBranchHeadsPath() {
    return myBranchHeadsPath;
  }


  public boolean isbranchHeadsFile(String filePath) {
    return filePath.equals(myBranchHeadsPath);
  }

  public boolean isBranchFile(String filePath) {
    return filePath.equals(myBranchPath);
  }

  public boolean isMergeFile(String filePath) {
    return filePath.startsWith(myMergePath);
  }
}
