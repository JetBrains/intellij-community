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

import com.intellij.dvcs.test.TestRepositoryUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepositoryReader;

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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHgDir = new File(myRepository.getPath(), ".hg");
    assertTrue(myHgDir.exists());
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));

    String pathToHg = "testData/repo/dot_hg";
    File testHgDir = new File(pluginRoot, FileUtil.toSystemDependentName(pathToHg));

    File cacheDir = new File(testHgDir, "cache");
    File testBranchFile = new File(testHgDir, "branch");
    File testBookmarkFile = new File(testHgDir, "bookmarks");
    File testCurrentBookmarkFile = new File(testHgDir, "bookmarks.current");
    FileUtil.copyDir(cacheDir, new File(myHgDir, "cache"));
    FileUtil.copy(testBranchFile, new File(myHgDir, "branch"));
    FileUtil.copy(testBookmarkFile, new File(myHgDir, "bookmarks"));
    FileUtil.copy(testCurrentBookmarkFile, new File(myHgDir, "bookmarks.current"));

    myRepositoryReader = new HgRepositoryReader(myHgDir);
    myBranches = readBranches();
    myBookmarks = readBookmarks();
  }

  public void testHEAD() {
    assertEquals("25e44c95b2612e3cdf29a704dabf82c77066cb67", myRepositoryReader.readCurrentRevision());
  }

  public void testCurrentBranch() {
    String currentBranch = myRepositoryReader.readCurrentBranch();
    assertEquals(currentBranch, "firstBranch");
  }

  public void testBranches() {
    Collection<String> branches = myRepositoryReader.readBranches();
    TestRepositoryUtil.assertEqualCollections(branches, myBranches);
  }

  public void testBookmarks() {
    Collection<String> bookmarks = myRepositoryReader.readBookmarks();
    TestRepositoryUtil.assertEqualCollections(bookmarks, myBookmarks);
  }

  @NotNull
  private Collection<String> readBranches() throws IOException {
    Collection<String> branches = new HashSet<String>();
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
  private Collection<String> readBookmarks() throws IOException {
    Collection<String> bookmarks = new HashSet<String>();
    File bookmarksFile = new File(myHgDir, "bookmarks");
    String[] bookmarksWithHashes = FileUtil.loadFile(bookmarksFile).split("\n");
    for (String str : bookmarksWithHashes) {
      String[] refAndName = str.trim().split(" ");
      assertEquals(2, refAndName.length);
      bookmarks.add(refAndName[1]);
    }
    return bookmarks;
  }
}
