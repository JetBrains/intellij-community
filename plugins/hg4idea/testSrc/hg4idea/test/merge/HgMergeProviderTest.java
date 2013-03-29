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
package hg4idea.test.merge;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.VirtualFile;
import hg4idea.test.HgPlatformTest;
import hg4idea.test.HgTestUtil;
import org.testng.Assert;

import java.io.IOException;

import static com.intellij.dvcs.test.Executor.*;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgMergeProviderTest extends HgPlatformTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testMerge2BranchesIfFileCreatedSeparatelyInBoth() throws VcsException {
    cd(myRepository);
    hg("branch branchA");
    hg("commit -m 'create branchA' ");
    touch(AFILE, "a");
    hg("add " + AFILE);
    hg("commit -m 'create file in branchA' ");
    hg("up default");
    touch(AFILE, "default");
    hg("add " + AFILE);
    hg("commit -m 'create file in default branch'");
    hg("merge branchA");
    verifyMergeData(myRepository.findChild(AFILE), "", "default", "a");
  }

  public void testMerge2Branches() throws VcsException {
    cd(myRepository);
    String FILENAME = "A.txt";
    touch(FILENAME, "base");
    hg("add " + FILENAME);
    hg("commit -m 'create file'");
    hg("branch branchA");
    hg("commit -m 'create branchA'");
    echo(FILENAME, " modify with a");
    hg("commit -m 'modify file in branchA'");
    hg("up default");
    echo(FILENAME, " modify with b");
    hg("commit -m 'modify file in default'");
    hg("merge branchA");
    verifyMergeData(myRepository.findChild(FILENAME), "base", "base modify with b", "base modify with a");
  }

  /**
   * Start with a file in both repositories.
   * 1. Edit the file in parent repository, commit the change.
   * 2. Edit the file in child repository, commit the change.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data.
   */
  public void testMergeWithCommittedLocalChange() throws Exception {
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;
    cd(myRepository);
    HgTestUtil.printToFile(parentFile, "server");
    hg("commit -m " + COMMIT_MESSAGE);
    // committing conflicting change
    cd(myChildRepo);
    HgTestUtil.printToFile(childFile, "local");
    hg("commit -m " + COMMIT_MESSAGE);
    hg("pull");
    hg("update");
    hg("merge");
    verifyMergeData(myChildRepo.findChild(childFile.getName()), "basic", "local", "server");
  }

  /**
   * Start with a file in both repositories.
   * 1. Edit the file in parent repository, commit the change.
   * 2. Edit the file in child repository, don't commit the change.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data.
   */
  public void testMergeWithUncommittedLocalChange() throws Exception {
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;
    cd(myRepository);
    HgTestUtil.printToFile(parentFile, "server");
    hg("commit -m " + COMMIT_MESSAGE);

    // uncommitted conflicting change
    cd(myChildRepo);
    HgTestUtil.printToFile(childFile, "local");
    hg("pull");
    hg("update");
    hg("merge");

    verifyMergeData(myChildRepo.findChild(childFile.getName()), "basic", "local", "server");
  }

  /**
   * Start with a non fresh repository.
   * 1. Add a file in parent repository, commit.
   * 2. Add a file with the same name, but different content in child repository, commit.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data (there is no basic version, but it shouldn't be null - just empty).
   */
  public void testFileAddedAndCommitted() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareFileInBothRepositories();
    cd(myRepository);
    touch(BFILE, "server");
    hg("add " + BFILE);
    hg("commit -m " + COMMIT_MESSAGE);

    cd(myChildRepo);
    touch(BFILE, "local");
    hg("add " + BFILE);
    hg("commit -m " + COMMIT_MESSAGE);

    hg("pull");
    hg("update");
    hg("merge");

    verifyMergeData(myChildRepo.findChild(BFILE), "", "local", "server");
  }

  /**
   * Start with a non fresh repository.
   * 1. Add a file in parent repository, commit.
   * 2. Add a file with the same name, but different content in child repository, don't commit.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data (there is no basic version, but it shouldn't be null - just empty).
   */
  public void testFileAddedNotCommited() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareFileInBothRepositories();
    cd(myRepository);
    touch(BFILE, "server");
    hg("add " + BFILE);
    hg("commit -m " + COMMIT_MESSAGE);

    cd(myChildRepo);
    touch(BFILE, "local");
    hg("add " + BFILE);

    hg("pull");
    hg("update");
    hg("merge");

    verifyMergeData(myChildRepo.findChild(BFILE), "", "local", "server");
  }


  /**
   * Creates a file with initial content in the parent repository, pulls & updates it to the child repository.
   *
   * @return References to the files in parent and child repositories respectively.
   */
  private Pair<VirtualFile, VirtualFile> prepareFileInBothRepositories() throws IOException {
    cd(myRepository);
    touch(AFILE, "basic");
    hg("add " + AFILE);
    hg("commit -m 'create file' ");
    cd(myChildRepo);
    hg("pull");
    hg("update");
    final VirtualFile childFile = myChildRepo.findChild(AFILE);
    return Pair.create(myRepository.findChild(AFILE), childFile);
  }

  private void verifyMergeData(final VirtualFile file, String expectedBase, String expectedLocal, String expectedServer)
    throws VcsException {
    final MergeData mergeData = myMergeProvider.loadRevisions(file);
    assertEquals(expectedBase, mergeData.ORIGINAL);
    assertEquals(expectedServer, mergeData.LAST);
    assertEquals(expectedLocal, mergeData.CURRENT);
  }

  private static void assertEquals(String s, byte[] bytes) {
    Assert.assertEquals(s, new String(bytes));
  }
}
