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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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
 * NB: works with {@link File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Hg file format.
 *
 * @author Nadya Zabrodina
 */
public class HgRepositoryReader {

  private static final Logger LOG = Logger.getInstance(HgRepositoryReader.class);

  private static final Pattern HASH_NAME = Pattern.compile("\\s*([0-9a-fA-F]{40})[:?|\\s+](.+)");
  private static final Pattern HASH_STATUS_NAME = Pattern.compile("\\s*([0-9a-fA-F]+)\\s+\\w\\s+(.+)");
  //hash + name_or_revision_num; hash + status_character +  name_or_revision_num

  @NotNull private final File myHgDir;            // .hg
  @NotNull private File myBranchHeadsFile;  // .hg/cache/branch* + part depends on version
  @NotNull private final File myCacheDir; // .hg/cache (does not exist before first commit)
  @NotNull private final File myCurrentBranch;    // .hg/branch
  @NotNull private final File myBookmarksFile; //.hg/bookmarks
  @NotNull private final File myCurrentBookmark; //.hg/bookmarks.current
  @NotNull private final File myTagsFile; //.hgtags  - not in .hg directory!!!
  @NotNull private final File myLocalTagsFile;  // .hg/localtags
  @NotNull private final File myDirStateFile;  // .hg/dirstate
  @NotNull private final File mySubrepoFile;  // .hgsubstate

  //mq internal files
  @NotNull private final File myMqInternalDir; //.hg/patches

  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;
  private final boolean myStatusInBranchFile;
  @NotNull private final HgVcs myVcs;

  public HgRepositoryReader(@NotNull HgVcs vcs, @NotNull File hgDir) {
    myHgDir = hgDir;
    DvcsUtil.assertFileExists(myHgDir, ".hg directory not found in " + myHgDir);
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
    mySubrepoFile = new File(myHgDir.getParentFile(), ".hgsubstate");
    myDirStateFile = new File(myHgDir, "dirstate");
    myMqInternalDir = new File(myHgDir, "patches");
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
      return Hex.encodeHexString(readHashBytesFromFile(myDirStateFile));
    }
    catch (IOException e) {
      // dirState exists if not fresh,  if we could not load dirState info repository must be corrupted
      LOG.error("IOException while trying to read current repository state information.", e);
      return null;
    }
  }

  @NotNull
  private static byte[] readHashBytesFromFile(@NotNull File file) throws IOException {
    byte[] bytes;
    final InputStream stream = new FileInputStream(file);
    try {
      bytes = FileUtil.loadBytes(stream, 20);
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
    String[] branchesWithHeads;
    try {
      branchesWithHeads = DvcsUtil.tryLoadFile(myBranchHeadsFile).split("\n");
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return null;
    }
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
    return branchExist() ? DvcsUtil.tryLoadFileOrReturn(myCurrentBranch, HgRepository.DEFAULT_BRANCH) : HgRepository.DEFAULT_BRANCH;
  }

  @NotNull
  public Map<String, LinkedHashSet<Hash>> readBranches() {
    Map<String, LinkedHashSet<Hash>> branchesWithHashes = new HashMap<>();
    // Set<String> branchNames = new HashSet<String>();
    if (isBranchInfoAvailable()) {
      Pattern activeBranchPattern = myStatusInBranchFile ? HASH_STATUS_NAME : HASH_NAME;
      String[] branchesWithHeads = DvcsUtil.tryLoadFileOrReturn(myBranchHeadsFile, "").split("\n");
      // first one - is a head revision: head hash + head number;
      for (int i = 1; i < branchesWithHeads.length; ++i) {
        Matcher matcher = activeBranchPattern.matcher(branchesWithHeads[i]);
        if (matcher.matches()) {
          String name = matcher.group(2);
          if (branchesWithHashes.containsKey(name)) {
            branchesWithHashes.get(name).add(myVcsObjectsFactory.createHash(matcher.group(1)));
          }
          else {
            LinkedHashSet<Hash> hashes = new LinkedHashSet<>();
            hashes.add(myVcsObjectsFactory.createHash(matcher.group(1)));
            branchesWithHashes.put(name, hashes);
          }
        }
      }
    }
    return branchesWithHashes;
  }

  private boolean isMergeInProgress() {
    return new File(myHgDir, "merge").exists();
  }

  private boolean hasSubrepos() {
    return mySubrepoFile.exists();
  }

  private boolean isRebaseInProgress() {
    return new File(myHgDir, "rebasestate").exists();
  }

  private boolean isCherryPickInProgress() {
    return new File(myHgDir, "graftstate").exists();
  }

  @NotNull
  public Repository.State readState() {
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
    else if (isCherryPickInProgress()) {
      return Repository.State.GRAFTING;
    }
    return isMergeInProgress() ? Repository.State.MERGING : Repository.State.NORMAL;
  }

  public boolean isFresh() {
    return !myCacheDir.exists();
  }

  private boolean branchExist() {
    return myCurrentBranch.exists();
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readBookmarks() {
    return readReferences(myBookmarksFile);
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readTags() {
    return readReferences(myTagsFile);
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readLocalTags() {
    return readReferences(myLocalTagsFile);
  }

  @NotNull
  private Collection<HgNameWithHashInfo> readReferences(@NotNull File fileWithReferences) {
    HashSet<HgNameWithHashInfo> result = ContainerUtil.newHashSet();
    readReferences(fileWithReferences, result);
    return result;
  }

  private void readReferences(@NotNull File fileWithReferences, @NotNull Collection<HgNameWithHashInfo> resultRefs) {
    // files like .hg/bookmarks which contains hash + name, f.e. 25e44c95b2612e3cdf29a704dabf82c77066cb67 A_BookMark or
    //files like .hg/patches/status hash:name, f.e. 25e44c95b2612e3cdf29a704dabf82c77066cb67:1.diff
    if (!fileWithReferences.exists()) return;

    String[] namesWithHashes = DvcsUtil.tryLoadFileOrReturn(fileWithReferences, "").split("\n");
    for (String str : namesWithHashes) {
      Matcher matcher = HASH_NAME.matcher(str);
      if (matcher.matches()) {
        resultRefs.add(new HgNameWithHashInfo(matcher.group(2), myVcsObjectsFactory.createHash(matcher.group(1))));
      }
    }
  }

  @Nullable
  public String readCurrentBookmark() {
    return myCurrentBookmark.exists() ? DvcsUtil.tryLoadFileOrReturn(myCurrentBookmark, "") : null;
  }

  @NotNull
  public Collection<HgNameWithHashInfo> readSubrepos() {
    if (!hasSubrepos()) return Collections.emptySet();
    return readReferences(mySubrepoFile);
  }

  @NotNull
  public List<HgNameWithHashInfo> readMQAppliedPatches() {
    ArrayList<HgNameWithHashInfo> mqPatchRefs = ContainerUtil.newArrayList();
    readReferences(new File(myMqInternalDir, "status"), mqPatchRefs);
    return mqPatchRefs;
  }

  @NotNull
  public List<String> readMqPatchNames() {
    File seriesFile = new File(myMqInternalDir, "series");
    return seriesFile.exists() ? StringUtil.split(DvcsUtil.tryLoadFileOrReturn(seriesFile, ""), "\n") : ContainerUtil.<String>emptyList();
  }
}
