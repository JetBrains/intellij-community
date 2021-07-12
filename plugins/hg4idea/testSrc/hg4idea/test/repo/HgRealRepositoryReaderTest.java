// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.vcs.VcsTestUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
import org.zmlx.hg4idea.repo.HgRepositoryReader;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Arrays;
import java.util.Collections;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;
import static hg4idea.test.HgExecutor.hgMergeWith;

public class HgRealRepositoryReaderTest extends HgPlatformTest {
  @NotNull private HgRepositoryReader myRepositoryReader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    createBranchesAndTags();
    myRepositoryReader = new HgRepositoryReader(myVcs, myRepository.toNioPath().resolve(".hg").toFile(), myRepository);
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
                                       Collections.singletonList("localTag"));
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
