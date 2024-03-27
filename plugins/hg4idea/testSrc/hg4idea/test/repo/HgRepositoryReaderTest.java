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

  public void testCurrentBookmark() {
    String bookmarkName = "bookmark_2";
    updateRepoTo(bookmarkName);
    assertEquals(bookmarkName, myRepositoryReader.readCurrentBookmark());
  }

}
