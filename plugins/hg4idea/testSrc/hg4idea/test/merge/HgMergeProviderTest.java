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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.VirtualFile;
import hg4idea.test.HgPlatformTest;
import org.testng.Assert;

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
