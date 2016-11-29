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
package hg4idea.test.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepositoryReader;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Nadya Zabrodina
 */
public class HgRepositoryReaderTest extends HgPlatformTest {

  @NotNull private HgRepositoryReader myRepositoryReader;
  @NotNull private File myHgDir;
  @NotNull private Collection<String> myBranches;
  @NotNull private Collection<String> myBookmarks;
  @NotNull private Collection<String> myTags;
  @NotNull private Collection<String> myLocalTags;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHgDir = new File(myRepository.getPath(), ".hg");
    assertTrue(myHgDir.exists());
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));

    String pathToHg = "testData/repo/dot_hg";
    File testHgDir = new File(pluginRoot, FileUtil.toSystemDependentName(pathToHg));

    File cacheDir = new File(testHgDir, "cache");
    File testDirStateFile = new File(testHgDir, "dirstate");
    File testBranchFile = new File(testHgDir, "branch");
    File testBookmarkFile = new File(testHgDir, "bookmarks");
    File testCurrentBookmarkFile = new File(testHgDir, "bookmarks.current");
    File testTagFile = new File(testHgDir.getParentFile(), ".hgtags");
    File testLocalTagFile = new File(testHgDir, "localtags");
    FileUtil.copyDir(cacheDir, new File(myHgDir, "cache"));
    FileUtil.copy(testBranchFile, new File(myHgDir, "branch"));
    FileUtil.copy(testDirStateFile, new File(myHgDir, "dirstate"));
    FileUtil.copy(testBookmarkFile, new File(myHgDir, "bookmarks"));
    FileUtil.copy(testCurrentBookmarkFile, new File(myHgDir, "bookmarks.current"));
    FileUtil.copy(testTagFile, new File(myHgDir.getParentFile(), ".hgtags"));
    FileUtil.copy(testLocalTagFile, new File(myHgDir, "localtags"));

    myRepositoryReader = new HgRepositoryReader(myVcs, myHgDir);
    myBranches = readBranches();
    myBookmarks = readRefs(testBookmarkFile);
    myTags = readRefs(testTagFile);
    myLocalTags = readRefs(testLocalTagFile);
  }

  public void testCurrentRecision() {
    assertEquals("33ef48b940a9797973c3becd9d3a181593f5b57a", myRepositoryReader.readCurrentRevision());
  }

  public void testTip() {
    assertEquals("25e44c95b2612e3cdf29a704dabf82c77066cb67", myRepositoryReader.readCurrentTipRevision());
  }

  public void testCurrentBranch() {
    String currentBranch = myRepositoryReader.readCurrentBranch();
    assertEquals(currentBranch, "firstBranch");
  }

  public void testBranches() {
    Collection<String> branches = myRepositoryReader.readBranches().keySet();
    VcsTestUtil.assertEqualCollections(branches, myBranches);
  }

  public void testBookmarks() {
    Collection<String> bookmarks = HgUtil.getNamesWithoutHashes(myRepositoryReader.readBookmarks());
    VcsTestUtil.assertEqualCollections(bookmarks, myBookmarks);
  }

  public void testTags() {
    Collection<String> tags = HgUtil.getNamesWithoutHashes(myRepositoryReader.readTags());
    VcsTestUtil.assertEqualCollections(tags, myTags);
  }

  public void testLocalTags() {
    Collection<String> localTags = HgUtil.getNamesWithoutHashes(myRepositoryReader.readLocalTags());
    VcsTestUtil.assertEqualCollections(localTags, myLocalTags);
  }

  @NotNull
  private Collection<String> readBranches() throws IOException {
    Collection<String> branches = new HashSet<>();
    File branchHeads = new File(new File(myHgDir, "cache"), "branchheads-served");
    String[] branchesWithHashes = FileUtil.loadFile(branchHeads).split("\n");
    for (int i = 1; i < branchesWithHashes.length; ++i) {
      String[] refAndName = branchesWithHashes[i].trim().split(" ");
      assertEquals(2, refAndName.length);
      branches.add(refAndName[1]);
    }
    return branches;
  }


  public void testCurrentBookmark() {
    assertEquals(myRepositoryReader.readCurrentBookmark(), "B_BookMark");
  }

  @NotNull
  private static Collection<String> readRefs(@NotNull File refFile) throws IOException {
    Collection<String> refs = new HashSet<>();
    String[] refsWithHashes = FileUtil.loadFile(refFile).split("\n");
    for (String str : refsWithHashes) {
      String[] refAndName = str.trim().split(" ");
      assertEquals(2, refAndName.length);
      refs.add(refAndName[1]);
    }
    return refs;
  }
}
