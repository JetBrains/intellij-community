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

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.vcs.VcsTestUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
import org.zmlx.hg4idea.repo.HgRepositoryReader;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.Arrays;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;
import static hg4idea.test.HgExecutor.hgMergeWith;

public class HgRealRepositoryReaderTest extends HgPlatformTest {

  @NotNull private HgRepositoryReader myRepositoryReader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File hgDir = new File(myRepository.getPath(), ".hg");
    assertTrue(hgDir.exists());
    createBranchesAndTags();
    myRepositoryReader = new HgRepositoryReader(myVcs, hgDir);
  }

  public void testMergeState() {
    hgMergeWith("branchB");
    assertEquals(myRepositoryReader.readState(), Repository.State.MERGING);
  }

  public void testState() {
    assertEquals(myRepositoryReader.readState(), Repository.State.NORMAL);
  }

  public void testCurrentBranch() {
    assertEquals(myRepositoryReader.readCurrentBranch(), "branchA");
  }

  public void testBranches() {
    VcsTestUtil.assertEqualCollections(myRepositoryReader.readBranches().keySet(),
                                       Arrays.asList("default", "branchA", "branchB"));
  }

  public void testOpenedBranches() {
    cd(myRepository);
    myRepository.refresh(false, true);
    HgRepository hgRepository = HgRepositoryImpl.getInstance(myRepository, myProject, myProject);
    hg("up branchA");
    hg("commit -m 'close branch' --close-branch");
    hgRepository.update();
    VcsTestUtil.assertEqualCollections(hgRepository.getOpenedBranches(),
                                       Arrays.asList("default", "branchB"));
  }

  public void testTags() {
    VcsTestUtil.assertEqualCollections(HgUtil.getNamesWithoutHashes(myRepositoryReader.readTags()),
                                       Arrays.asList("tag1", "tag2"));
  }

  public void testLocalTags() {
    VcsTestUtil.assertEqualCollections(HgUtil.getNamesWithoutHashes(myRepositoryReader.readLocalTags()),
                                       Arrays.asList("localTag"));
  }

  public void testCurrentBookmark() {
    hg("update B_BookMark");
    assertEquals(myRepositoryReader.readCurrentBookmark(), "B_BookMark");
  }

  public void testBookmarks() {
    VcsTestUtil.assertEqualCollections(HgUtil.getNamesWithoutHashes(myRepositoryReader.readBookmarks()),
                                       Arrays.asList("A_BookMark", "B_BookMark", "C_BookMark"));
  }

  private void createBranchesAndTags() {
    cd(myRepository);
    hg("bookmark A_BookMark");
    hg("tag tag1");
    String aFile = "A.txt";
    touch(aFile, "base");
    hg("add " + aFile);
    hg("commit -m 'create file'");
    hg("bookmark B_BookMark");
    hg("branch branchA");
    hg("tag tag2");
    echo(aFile, " modify with a");
    hg("commit -m 'create branchA'");
    hg("up default");
    hg("branch branchB");
    hg("tag -l localTag");
    echo(aFile, " modify with b");
    hg("commit -m 'modify file in branchB'");
    hg("bookmark C_BookMark");
    hg("up branchA");
  }
}
