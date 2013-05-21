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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information about the Hg repository from Hg service files located in the {@code .hg} folder.
 * NB: works with {@link java.io.File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link HgRepoStateException} in the case of incorrect Hg file format.
 *
 * @author Nadya Zabrodina
 */
class HgRepositoryReader {

  private static final Logger LOG = Logger.getInstance(HgRepositoryReader.class);
  private static Pattern BRANCH_PATTERN = Pattern.compile("\\s*(.+)\\s+(.+)");

  private static final int IO_RETRIES = 3; // number of retries before fail if an IOException happens during file read.

  private final File myHgDir;         // .hg/
  private final File myBranchHeadsFile;       // .hg/cache/branchheads -  this file does not exist before first commit
  private final File myCurrentBranch; // .hg/branch

  HgRepositoryReader(@NotNull File hgDir) {
    myHgDir = hgDir;
    assertFileExists(myHgDir, ".hg directory not found in " + hgDir);
    myBranchHeadsFile = new File(new File(myHgDir, "cache"), "branchheads");
    myCurrentBranch = new File(myHgDir, "branch");
  }


  /**
   * Finds current revision value.
   *
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  String readCurrentRevision() {
    String[] branchesWithHeads = tryLoadFile(myBranchHeadsFile).trim().split("\n");
    String head = branchesWithHeads[0];
    Matcher matcher = BRANCH_PATTERN.matcher(head);
    if (matcher.matches()) {
      return (matcher.group(1));
    }
    return null;
  }

  /**
   * Return current branch
   */
  @NotNull
  String readCurrentBranch() {
    if (branchExist()) {
      String rev = tryLoadFile(myCurrentBranch);
      return rev.trim();
    }
    return HgRepository.DEFAULT_BRANCH;
  }


  List<String> readBranches() {
    List<String> branches = new ArrayList<String>();
    String[] branchesWithHeads = tryLoadFile(myBranchHeadsFile).trim().split("\n");
    // first one - is a head revision: head hash + head number;
    for (int i = 1; i < branchesWithHeads.length; ++i) {
      Matcher matcher = BRANCH_PATTERN.matcher(branchesWithHeads[i]);
      if (matcher.matches()) {
        branches.add(matcher.group(2));
      }
    }
    return branches;
  }


  private static void assertFileExists(File file, String message) {
    if (!file.exists()) {
      throw new HgRepoStateException(message);
    }
  }

  /**
   * Loads the file content.
   * Tries 3 times, then a {@link HgRepoStateException} is thrown.
   *
   * @param file File to read.
   * @return file content.
   */
  @NotNull
  private static String tryLoadFile(final File file) {
    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return FileUtil.loadFile(file);
      }
    }, file);
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link HgRepoStateException}.
   * If an other exception happens, rethrows it as a {@link HgRepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  private static String tryOrThrow(Callable<String> actionToTry, File fileToLoad) {
    IOException cause = null;
    for (int i = 0; i < IO_RETRIES; i++) {
      try {
        return actionToTry.call();
      }
      catch (IOException e) {
        LOG.info("IOException while loading " + fileToLoad, e);
        cause = e;
      }
      catch (Exception e) {    // this shouldn't happen since only IOExceptions are thrown in clients.
        throw new HgRepoStateException("Couldn't load file " + fileToLoad, e);
      }
    }
    throw new HgRepoStateException("Couldn't load file " + fileToLoad, cause);
  }

  public HgRepository.State readState() {
    if (isMergeInProgress()) {
      return HgRepository.State.MERGING;
    }
    return HgRepository.State.NORMAL;
  }

  private boolean isMergeInProgress() {
    File mergeFile = new File(myHgDir, "merge");
    return mergeFile.exists();
  }

  public boolean headExist() {
    return myBranchHeadsFile.exists();
  }

  public boolean branchExist() {
    return myCurrentBranch.exists();
  }
}
