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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
  private static Pattern HASH_STATUS_NAME = Pattern.compile("\\s*([0-9a-fA-F]+)\\s+\\w\\s+(.+)");
    //hash + name_or_revision_num; hash + status_character +  name_or_revision_num

  @NotNull private final File myHgDir;            // .hg
  @NotNull private  File myBranchHeadsFile;  // .hg/cache/branch* + part depends on version
  @NotNull private final File myCacheDir; // .hg/cache (does not exist before first commit)
  @NotNull private final File myCurrentBranch;    // .hg/branch
  @NotNull private final File myBookmarksFile; //.hg/bookmarks
  @NotNull private final File myCurrentBookmark; //.hg/bookmarks.current
  @NotNull private final File myTagsFile; //.hgtags  - not in .hg directory!!!
  @NotNull private final File myLocalTagsFile;  // .hg/localtags
  @NotNull private final File myDirStateFile;  // .hg/dirstate
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;
  private final boolean myStatusInBranchFile;
  @NotNull final HgVcs myVcs;

  public HgRepositoryReader(@NotNull HgVcs vcs, @NotNull File hgDir) {
    myHgDir = hgDir;
    RepositoryUtil.assertFileExists(myHgDir, ".hg directory not found in " + myHgDir);
    myVcs = vcs;
    HgVersion version = myVcs.getVersion();
    myStatusInBranchFile = version.hasBranch2();
    myCacheDir = new File(myHgDir, "cache");
    myBranchHeadsFile = identifyBranchHeadFile(version, myCacheDir);
    myCurrentBranch = new File(myHgDir, "branch");
    myBookmarksFile = new File(myHgDir, "bookmarks");
    myCurrentBookmark = new File(myHgDir, "bookmarks.current");
    myLocalTagsFile = new File(myHgDir, "localtags");
    myTagsFile = new File(myHgDir.getParentFile(), ".hgtags");
    myDirStateFile = new File(myHgDir, "dirstate");
    myVcsObjectsFactory = ServiceManager.getService(vcs.getProject(), VcsLogObjectsFactory.class);
  }

  /**
   * Identify file with branches and heads information depends on hg version;
   */
  @NotNull
  private static File identifyBranchHeadFile(@NotNull HgVersion version, @NotNull File parentCacheFile) {
    //before 2.5 only branchheads exist; branchheads-served after mercurial 2.5; branch2-served after 2.9;
    // when project is  recently  cloned there are only base file
    if (version.hasBranch2()) {
      File file = new File(parentCacheFile, "branch2-served");
      return file.exists() ? file : new File(parentCacheFile, "branch2-base");
    }
    if (version.hasBranchHeadsBaseServed()) {
      File file = new File(parentCacheFile, "branchheads-served");
      return file.exists() ? file : new File(parentCacheFile, "branchheads-base");
    }
    return new File(parentCacheFile, "branchheads");
  }

  /**
   * Finds current revision value.
   *
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  public String readCurrentRevision() {
    if (!isDirStateInfoAvailable()) return null;
    try {
      return Hex.encodeHexString(readBytesFromFile(myDirStateFile, 20));
    }
    catch (IOException e) {
      // dirState exists if not fresh,  if we could not load dirState info repository must be corrupted
      throw new RepoStateException("IOException while trying to read current repository state information.", e);
    }
  }

  @NotNull
  public byte[] readBytesFromFile(@NotNull File file, int len) throws IOException {
    byte[] bytes;
    final InputStream stream = new FileInputStream(file);
    try {
      bytes = FileUtil.loadBytes(stream, len);
    }
    finally {
      stream.close();
    }
    return bytes;
  }

  /**
   * Finds tip revision value.
   *
   * @return The tip revision hash, or <b>{@code null}</b> if tip revision is unknown - it is the initial repository state.
   */
  @Nullable
  public String readCurrentTipRevision() {
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
    myBranchHeadsFile = identifyBranchHeadFile(myVcs.getVersion(), myCacheDir);
    return !isFresh() && myBranchHeadsFile.exists();
  }

  private boolean isDirStateInfoAvailable() {
    return myDirStateFile.exists();
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
      Pattern activeBranchPattern = myStatusInBranchFile ? HASH_STATUS_NAME : HASH_NAME;
      String[] branchesWithHeads = RepositoryUtil.tryLoadFile(myBranchHeadsFile).split("\n");
      // first one - is a head revision: head hash + head number;
      for (int i = 1; i < branchesWithHeads.length; ++i) {
        Matcher matcher = activeBranchPattern.matcher(branchesWithHeads[i]);
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

  public boolean isRebaseInProgress() {
    return new File(myHgDir, "rebasestate").exists();
  }

  @NotNull
  public Repository.State readState() {
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
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
