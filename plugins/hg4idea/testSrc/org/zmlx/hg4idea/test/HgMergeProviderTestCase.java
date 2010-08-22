/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.HgVcs;

import java.io.IOException;

import static org.testng.Assert.assertNotNull;

/**
 * Tests HgMergeProvider for different merge situations.
 * All Mercurial operations are performed natively to test only HgMergeProvider functionality.
 * @author Kirill Likhodedov
 */
public class HgMergeProviderTestCase extends HgCollaborativeTestCase {

  private MergeProvider myMergeProvider;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMergeProvider = HgVcs.getInstance(myProject).getMergeProvider();
    assertNotNull(myMergeProvider);
  }

  /**
   * Start with a file in both repositories.
   * 1. Edit the file in parent repository, commit the change.
   * 2. Edit the file in child repository, commit the change.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data.
   */
  @Test
  public void mergeWithCommittedLocalChange() throws Exception {
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;

    HgTestUtil.printToFile(parentFile, "server");
    myParentRepo.commit();
    // committing conflicting change
    HgTestUtil.printToFile(childFile, "local");
    myRepo.commit();

    myRepo.pullUpdateMerge();

    verifyMergeData(childFile, "basic", "local", "server");
  }

  /**
   * Start with a file in both repositories.
   * 1. Edit the file in parent repository, commit the change.
   * 2. Edit the file in child repository, don't commit the change.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data.
   */
  @Test
  public void mergeWithUncommittedLocalChange() throws Exception {
    final Pair<VirtualFile, VirtualFile> files = prepareFileInBothRepositories();
    final VirtualFile parentFile = files.first;
    final VirtualFile childFile = files.second;

    HgTestUtil.printToFile(parentFile, "server");
    myParentRepo.commit();

    // uncommitted conflicting change
    HgTestUtil.printToFile(childFile, "local");

    myRepo.pullUpdateMerge();
    
    verifyMergeData(childFile, "basic", "local", "server");
  }

  /**
   * Start with a non fresh repository.
   * 1. Add a file in parent repository, commit.
   * 2. Add a file with the same name, but different content in child repository, commit.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data (there is no basic version, but it shouldn't be null - just empty).
   */
  @Test
  public void fileAddedAndCommitted() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareFileInBothRepositories();

    myParentRepo.createFile("b.txt", "server");
    myParentRepo.addCommit();

    final VirtualFile childFile = myRepo.createFile("b.txt", "local");
    myRepo.addCommit();

    myRepo.pullUpdateMerge();

    verifyMergeData(childFile, "", "local", "server");
  }

  /**
   * Start with a non fresh repository.
   * 1. Add a file in parent repository, commit.
   * 2. Add a file with the same name, but different content in child repository, don't commit.
   * 3. Update.
   * 4. Test the MergeData from the MergeProvider to have correct data (there is no basic version, but it shouldn't be null - just empty).
   */
  @Test
  public void fileAddedNotCommited() throws Exception {
    // this is needed to have the same root changeset - otherwise conflicting root changeset will cause
    // an error during 'hg pull': "abort: repository is unrelated"
    prepareFileInBothRepositories();

    myParentRepo.createFile("b.txt", "server");
    myParentRepo.addCommit();

    final VirtualFile childFile = myRepo.createFile("b.txt", "local");
    myRepo.add();

    myRepo.pullUpdateMerge();

    verifyMergeData(childFile, "", "local", "server");
  }

  private void verifyMergeData(VirtualFile file, String expectedBase, String expectedLocal, String expectedServer) throws Exception {
    final MergeData mergeData = myMergeProvider.loadRevisions(file);
    assertEquals(mergeData.ORIGINAL, expectedBase);
    assertEquals(mergeData.CURRENT, expectedLocal);
    assertEquals(mergeData.LAST, expectedServer);
  }

  private static void assertEquals(byte[] bytes, String s) {
    Assert.assertEquals(new String(bytes), s);
  }

  /**
   * Creates a file with initial content in the parent repository, pulls & updates it to the child repository.
   * @return References to the files in parent and child repositories respectively.
   */
  private Pair<VirtualFile, VirtualFile> prepareFileInBothRepositories() throws IOException {
    final VirtualFile parentFile = myParentRepo.createFile("a.txt", "basic");
    myParentRepo.add();
    myParentRepo.commit();
    myRepo.pull();
    myRepo.update();
    return Pair.create(parentFile, myRepo.getDirFixture().getFile("a.txt"));
  }

}
