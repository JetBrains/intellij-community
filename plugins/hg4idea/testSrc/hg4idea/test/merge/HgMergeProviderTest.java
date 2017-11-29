/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package hg4idea.test.merge;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import hg4idea.test.HgPlatformTest;
import hg4idea.test.HgTestUtil;
import org.testng.Assert;
import org.zmlx.hg4idea.HgVcs;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.*;

public class HgMergeProviderTest extends HgPlatformTest {
  protected MergeProvider myMergeProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    HgVcs vcs = HgVcs.getInstance(myProject);
    assertNotNull(vcs);
    myMergeProvider = vcs.getMergeProvider();
    assertNotNull(myMergeProvider);
  }

  public void testMerge2BranchesIfFileCreatedSeparatelyInBoth() {
    cd(myRepository);
    hg("branch branchA");
    hg("commit -m 'create branchA' ");
    String aFile = "A.txt";
    touch(aFile, "a");
    hg("add " + aFile);
    hg("commit -m 'create file in branchA' ");
    hg("up default");
    touch(aFile, "default");
    hg("add " + aFile);
    hg("commit -m 'create file in default branch'");
    hgMergeWith("branchA");
    myRepository.refresh(false, true);
    verifyMergeData(myRepository.findChild(aFile), "", "default", "a");
  }

  public void testMerge2Branches() {
    cd(myRepository);
    String aFile = "A.txt";
    touch(aFile, "base");
    hg("add " + aFile);
    hg("commit -m 'create file'");
    hg("branch branchA");
    hg("commit -m 'create branchA'");
    echo(aFile, " modify with a");
    hg("commit -m 'modify file in branchA'");
    hg("up default");
    echo(aFile, " modify with b");
    hg("commit -m 'modify file in default'");
    hgMergeWith("branchA");
    myRepository.refresh(false, true);
    verifyMergeData(myRepository.findChild(aFile), "base", "base modify with b", "base modify with a");
  }

  public void testMergeWithCommittedLocalChange() throws Exception {
    prepareSecondRepository();
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;
    //Edit the file in parent repository, commit the change.
    cd(myRepository);
    HgTestUtil.printToFile(parentFile, "server");
    hg("commit -m " + COMMIT_MESSAGE);
    //Edit the file in child repository, commit the change.
    cd(myChildRepo);
    HgTestUtil.printToFile(childFile, "local");
    hg("commit -m " + COMMIT_MESSAGE);
    updateProject();
    //committing conflicting change
    verifyMergeData(childFile, "basic", "local", "server");
  }

  public void testMergeWithUncommittedLocalChange() throws Exception {
    prepareSecondRepository();
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;
    //Edit the file in parent repository, commit the change.
    cd(myRepository);
    HgTestUtil.printToFile(parentFile, "server");
    hg("commit -m " + COMMIT_MESSAGE);

    // Edit the file in child repository, don't commit the change.
    cd(myChildRepo);
    HgTestUtil.printToFile(childFile, "local");
    updateProject();
    //uncommitted conflicting change
    verifyMergeData(childFile, "basic", "local", "server");
  }

  public void testFileAddedAndCommitted() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareSecondRepository();
    prepareFileInBothRepositories();
    //Add a file in parent repository, commit.
    cd(myRepository);
    String bFile = "B.txt";
    touch(bFile, "server");
    hg("add " + bFile);
    hg("commit -m " + COMMIT_MESSAGE);
    //Add a file with the same name, but different content in child repository, commit.
    cd(myChildRepo);
    touch(bFile, "local");
    myChildRepo.refresh(false, true);
    hg("add " + bFile);
    hg("commit -m " + COMMIT_MESSAGE);

    updateProject();

    verifyMergeData(myChildRepo.findChild(bFile), "", "local", "server");
  }

  public void testFileAddedNotCommitted() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareSecondRepository();
    prepareFileInBothRepositories();
    // Add a file in parent repository, commit.
    cd(myRepository);
    String bFile = "B.txt";
    touch(bFile, "server");
    hg("add " + bFile);
    hg("commit -m " + COMMIT_MESSAGE);
    //Add a file with the same name, but different content in child repository, don't commit.
    cd(myChildRepo);
    touch(bFile, "local");
    myChildRepo.refresh(false, true);
    hg("add " + bFile);

    updateProject();
    verifyMergeData(myChildRepo.findChild(bFile), "", "local", "server");
  }


  /**
   * Creates a file with initial content in the parent repository, pulls & updates it to the child repository.
   *
   * @return References to the files in parent and child repositories respectively.
   */
  private Pair<VirtualFile, VirtualFile> prepareFileInBothRepositories() {
    cd(myRepository);
    String aFile = "A.txt";
    touch(aFile, "basic");
    hg("add " + aFile);
    hg("commit -m 'create file' ");
    hg("update");
    myRepository.refresh(false, true);
    final VirtualFile parentFile = myRepository.findChild(aFile);
    assertNotNull("Can't find " + aFile + " in parent repo!", parentFile);
    cd(myChildRepo);
    hg("pull");
    hg("update");
    myChildRepo.refresh(false, true);
    final VirtualFile childFile = myChildRepo.findChild(aFile);
    return Pair.create(parentFile, childFile);
  }

  private void verifyMergeData(final VirtualFile file, String expectedBase, String expectedLocal, String expectedServer) {
    EdtTestUtil.runInEdtAndWait(() -> {
      MergeData mergeData = myMergeProvider.loadRevisions(file);
      assertEquals(expectedBase, mergeData.ORIGINAL);
      assertEquals(expectedServer, mergeData.LAST);
      assertEquals(expectedLocal, mergeData.CURRENT);
    });
  }

  private static void assertEquals(String s, byte[] bytes) {
    Assert.assertEquals(s, new String(bytes, CharsetToolkit.UTF8_CHARSET));
  }
}
