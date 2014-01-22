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

import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information about the Hg repository from Hg service files located in the {@code .hg} folder.
 * NB: works with {@link java.io.File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Hg file format.
 *
 * @author Nadya Zabrodina
 */
public class HgRepositoryReader {

  private static Pattern HASH_NAME = Pattern.compile("\\s*([0-9a-fA-F]+)\\s+(.+)");

  @NotNull private final File myHgDir;            // .hg
  @NotNull private final File myBranchHeadsFile;  // .hg/cache/branchheads
  @NotNull private final File myCacheDir; // .hg/cache (does not exist before first commit)
  @NotNull private final File myCurrentBranch;    // .hg/branch
  @NotNull private final File myBookmarksFile; //.hg/bookmarks
  @NotNull private final File myCurrentBookmark; //.hg/bookmarks.current
  @NotNull private final File myTagsFile; //.hgtags  - not in .hg directory!!!
  @NotNull private final File myLocalTagsFile;  // .hg/localtags
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public HgRepositoryReader(@NotNull Project project, @NotNull File hgDir) {
    myHgDir = hgDir;
    RepositoryUtil.assertFileExists(myHgDir, ".hg directory not found in " + myHgDir);
    myCacheDir = new File(myHgDir, "cache");
    myBranchHeadsFile = identifyBranchHeadFile(project, myCacheDir);
    myCurrentBranch = new File(myHgDir, "branch");
    myBookmarksFile = new File(myHgDir, "bookmarks");
    myCurrentBookmark = new File(myHgDir, "bookmarks.current");
    myLocalTagsFile = new File(myHgDir, "localtags");
    myTagsFile = new File(myHgDir.getParentFile(), ".hgtags");
    myVcsObjectsFactory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
  }

  /**
   * Identify file with branches and heads information depends on hg version;
   *
   * @param project
   * @param parentCacheFile
   * @return
   */
  @NotNull
  private static File identifyBranchHeadFile(@NotNull Project project, @NotNull File parentCacheFile) {
    File branchesFile = new File(parentCacheFile, "branchheads-served");  //branchheads-served exist after mercurial 2.5,
    //before 2.5 only branchheads exist
    HgVcs vcs = HgVcs.getInstance(project);
    return vcs != null && vcs.getVersion().hasBranchHeadsServed() ? branchesFile : new File(parentCacheFile, "branchheads");
  }

  /**
   * Finds current revision value.
   *
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  public String readCurrentRevision() {
    if (!isBranchInfoAvailable()) return null;
    String[] branchesWithHeads = RepositoryUtil.tryLoadFile(myBranchHeadsFile).split("\n");
    String head = branchesWithHeads[0];
    Matcher matcher = HASH_NAME.matcher(head);
    if (matcher.matches()) {
      return (matcher.group(1));
    }
    return null;
  }

  private boolean isBranchInfoAvailable() {
    return !isFresh() && myBranchHeadsFile.exists();
  }

  /**
   * Return current branch
   */
  @NotNull
  public String readCurrentBranch() {
    return branchExist() ? RepositoryUtil.tryLoadFile(myCurrentBranch) : HgRepository.DEFAULT_BRANCH;
  }

  @NotNull
  public Map<String, Set<Hash>> readBranches() {
    Map<String, Set<Hash>> branchesWithHashes = new HashMap<String, Set<Hash>>();
    // Set<String> branchNames = new HashSet<String>();
    if (isBranchInfoAvailable()) {
      String[] branchesWithHeads = RepositoryUtil.tryLoadFile(myBranchHeadsFile).split("\n");
      // first one - is a head revision: head hash + head number;
      for (int i = 1; i < branchesWithHeads.length; ++i) {
        Matcher matcher = HASH_NAME.matcher(branchesWithHeads[i]);
        if (matcher.matches()) {
          String name = matcher.group(2);
          if (branchesWithHashes.containsKey(name)) {
            branchesWithHashes.get(name).add(myVcsObjectsFactory.createHash(matcher.group(1)));
          }
          else {
            Set<Hash> hashes = new HashSet<Hash>();
            hashes.add(myVcsObjectsFactory.createHash(matcher.group(1)));
            branchesWithHashes.put(name, hashes);
          }
        }
      }
    }
    return branchesWithHashes;
  }

  public boolean isMergeInProgress() {
    return new File(myHgDir, "merge").exists();
  }

  @NotNull
  public Repository.State readState() {
    return isMergeInProgress() ? Repository.State.MERGING : Repository.State.NORMAL;
  }

  public boolean isFresh() {
    return !myCacheDir.exists();
  }

  public boolean branchExist() {
    return myCurrentBranch.exists();
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readBookmarks() {
    return readReference(myBookmarksFile);
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readTags() {
    return readReference(myTagsFile);
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readLocalTags() {
    return readReference(myLocalTagsFile);
  }

  @NotNull
  private Collection<HgNameWithHashInfo> readReference(@NotNull File fileWithReferences) {
    // files like .hg/bookmarks which contains hash + name, f.e. 25e44c95b2612e3cdf29a704dabf82c77066cb67 A_BookMark
    Set<HgNameWithHashInfo> refs = new HashSet<HgNameWithHashInfo>();
    if (!fileWithReferences.exists()) {
      return refs;
    }
    String[] namesWithHashes = RepositoryUtil.tryLoadFile(fileWithReferences).split("\n");
    for (String str : namesWithHashes) {
      Matcher matcher = HASH_NAME.matcher(str);
      if (matcher.matches()) {
        refs.add(new HgNameWithHashInfo(matcher.group(2), myVcsObjectsFactory.createHash(matcher.group(1))));
      }
    }
    return refs;
  }

  @Nullable
  public String readCurrentBookmark() {
    return myCurrentBookmark.exists() ? RepositoryUtil.tryLoadFile(myCurrentBookmark) : null;
  }
}
