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
import hg4idea.test.HgExecutor;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepositoryReader;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import static com.intellij.openapi.vcs.Executor.*;

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
    //File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));

    //String pathToHg = "testData/repo/dot_hg";
    //File testHgDir = new File(pluginRoot, FileUtil.toSystemDependentName(pathToHg));

    //File cacheDir = new File(testHgDir, "cache");
    //File testDirStateFile = new File(testHgDir, "dirstate");
    //File testBranchFile = new File(testHgDir, "branch");
    //File testBookmarkFile = new File(testHgDir, "bookmarks");
    //File testCurrentBookmarkFile = new File(testHgDir, "bookmarks.current");
    //File testTagFile = new File(testHgDir.getParentFile(), ".hgtags");
    //File testLocalTagFile = new File(testHgDir, "localtags");
    //FileUtil.copyDir(cacheDir, new File(myHgDir, "cache"));
    //FileUtil.copy(testBranchFile, new File(myHgDir, "branch"));
    //FileUtil.copy(testDirStateFile, new File(myHgDir, "dirstate"));
    //FileUtil.copy(testBookmarkFile, new File(myHgDir, "bookmarks"));
    //FileUtil.copy(testCurrentBookmarkFile, new File(myHgDir, "bookmarks.current"));
    //FileUtil.copy(testTagFile, new File(myHgDir.getParentFile(), ".hgtags"));
    //FileUtil.copy(testLocalTagFile, new File(myHgDir, "localtags"));

    myRepositoryReader = new HgRepositoryReader(myVcs, myHgDir, myRepository);
    myBranches = setUpBranches();
    myBookmarks = setUpBookmarks();
    myTags = setUpTags();
    myLocalTags = setUpLocalTags();
  }

  /**
   * create local tags in test repo
   * @return created local tags
   */
  @NotNull
  private Collection<String> setUpLocalTags(){
    cd(myRepository);
    touch("file_for_tag_local_1.txt");
    HgExecutor.hg("add file_for_tag_local_1.txt");
    HgExecutor.hg("commit -m 'commit for tag_local_1'");
    HgExecutor.hg("tag -l tag_local_1");
    touch("file_for_tag_local_2.txt");
    HgExecutor.hg("add file_for_tag_local_2.txt");
    HgExecutor.hg("commit -m 'commit for tag_local_2'");
    HgExecutor.hg("tag -l tag_local_2");
    Collection<String> tags = new HashSet<>();
    tags.add("tag_local_1");
    tags.add("tag_local_2");
    return tags;
  }

  /**
   * create tags in test repo
   * @return created tags
   */
  @NotNull
  private Collection<String> setUpTags(){
    cd(myRepository);
    touch("file_for_tag_1.txt");
    HgExecutor.hg("add file_for_tag_1.txt");
    HgExecutor.hg("commit -m 'commit for tag_1'");
    HgExecutor.hg("tag tag_1");
    touch("file_for_tag_2.txt");
    HgExecutor.hg("add file_for_tag_2.txt");
    HgExecutor.hg("commit -m 'commit for tag_2'");
    HgExecutor.hg("tag tag_2");
    Collection<String> tags = new HashSet<>();
    tags.add("tag_1");
    tags.add("tag_2");
    return tags;
  }

  /**
   * create bookmark in test repo
   * @return created bookmark
   */
  @NotNull
  private Collection<String> setUpBookmarks(){
    cd(myRepository);
    touch("file_for_bookmark_1.txt");
    HgExecutor.hg("add file_for_bookmark_1.txt");
    HgExecutor.hg("commit -m 'commit for bookmark_1'");
    HgExecutor.hg("bookmark bookmark_1");
    touch("file_for_bookmark_2.txt");
    HgExecutor.hg("add file_for_bookmark_2.txt");
    HgExecutor.hg("commit -m 'commit for bookmark_2'");
    HgExecutor.hg("bookmark bookmark_2");
    Collection<String> bookmarks = new HashSet<>();
    bookmarks.add("bookmark_1");
    bookmarks.add("bookmark_2");
    return bookmarks;
  }

  /**
   * create branch in test repo
   * @return created branch
   */
  @NotNull
  private Collection<String> setUpBranches(){
    cd(myRepository);
    HgExecutor.hg("update default");
    HgExecutor.hg("branch branch_1");
    touch("file_for_branch_1.txt");
    HgExecutor.hg("add file_for_branch_1.txt");
    HgExecutor.hg("commit -m 'commit for branch_1'");
    HgExecutor.hg("branch branch_2");
    touch("file_for_branch_2.txt");
    HgExecutor.hg("add file_for_branch_2.txt");
    HgExecutor.hg("commit -m 'commit for branch_2'");
    Collection<String> branches = new HashSet<>();
    branches.add("default");
    branches.add("branch_1");
    branches.add("branch_2");
    return branches;
  }

  /**
   * update current repo to branch/bookmark/tag
   * @param updateToName BranchName/TagName/Bookmark
   */
  private void updateRepoTo(String updateToName){
    cd(myRepository);
    HgExecutor.hg("update "+updateToName);
  }

  /**
   * get revision hash from repo for hg operation
   * @param operation it can be any that return revision
   * @return full revision hash
   */
  private String revisionFullHashForCommand(String operation){
    cd(myRepository);
    String output = HgExecutor.hg(String.format("%s --template '{node}'", operation));
    output = output.trim().strip();
    return output;
  }

  public void testCurrentRevision() {
    String currentRevision = revisionFullHashForCommand("parent");
    assertEquals(currentRevision, myRepositoryReader.readCurrentRevision());
  }

  public void testTip() {
    String tipRevision = revisionFullHashForCommand("tip");
    assertEquals(tipRevision, myRepositoryReader.readCurrentTipRevision());
  }

  public void testCurrentBranch() {
    String myCurrentBranch = "branch_1";
    updateRepoTo(myCurrentBranch);
    String currentBranch = myRepositoryReader.readCurrentBranch();
    assertEquals(myCurrentBranch, currentBranch);
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

  //@NotNull
  //private Collection<String> readBranches() throws IOException {
  //  Collection<String> branches = new HashSet<>();
  //  File branchHeads = new File(new File(myHgDir, "cache"), "branchheads-served");
  //  String[] branchesWithHashes = FileUtil.loadFile(branchHeads).split("\n");
  //  for (int i = 1; i < branchesWithHashes.length; ++i) {
  //    String[] refAndName = branchesWithHashes[i].trim().split(" ");
  //    assertEquals(2, refAndName.length);
  //    branches.add(refAndName[1]);
  //  }
  //  return branches;
  //}


  public void testCurrentBookmark() {
    String bookmarkName = "bookmark_2";
    updateRepoTo(bookmarkName);
    assertEquals(bookmarkName, myRepositoryReader.readCurrentBookmark());
  }

  //@NotNull
  //private static Collection<String> readRefs(@NotNull File refFile) throws IOException {
  //  Collection<String> refs = new HashSet<>();
  //  String[] refsWithHashes = FileUtil.loadFile(refFile).split("\n");
  //  for (String str : refsWithHashes) {
  //    String[] refAndName = str.trim().split(" ");
  //    assertEquals(2, refAndName.length);
  //    refs.add(refAndName[1]);
  //  }
  //  return refs;
  //}
}
