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
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgVcs;

import static com.intellij.dvcs.test.Executor.*;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgMergeProviderTest extends HgPlatformTest {
  private MergeProvider myMergeProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    HgVcs vcs = HgVcs.getInstance(myProject);
    assertNotNull(vcs);
    myMergeProvider = vcs.getMergeProvider();
    assertNotNull(myMergeProvider);
  }

  public void testMerge2BranchesIfFileCreatedSeparatelyInBoth() {
    cd(myRepository);
    hg("branch branchB");
    hg("commit -m \"create branchB\"");
    hg("branch branchA");
    hg("commit -m \"create branchA\"");
    touch("A.txt", "a");
    hg("add A.txt");
    hg("commit -m \"create A.txt in branchA\"");
    echo("A.txt", "aaa");
    hg("commit -m \"modify A.txt in branchA\"");
    hg("up branchB");
    touch("A.txt", "b");
    hg("add A.txt");
    hg("commit -m \"create A.txt in branchB\"");
    echo("A.txt", "bbb");
    hg("commit -m \"modify A.txt in branchB\"");
    hg("merge branchA");
    verifyMergeData(myRepository.findChild("A.txt"), "", "bbbb", "aaaa");
  }

  public void testMerge2Branches() {
    cd(myRepository);
    touch("A.txt", "base");
    hg("add A.txt");
    hg("commit -m \"create A.txt \"");
    hg("branch branchB");
    hg("commit -m \"create branchB\"");
    hg("branch branchA");
    hg("commit -m \"create branchA\"");
    echo("A.txt", "a");
    hg("commit -m \"modify A.txt in branchA\"");
    echo("A.txt", "aaa");
    hg("commit -m \"modify A.txt in branchA\"");
    hg("up branchB");
    echo("A.txt", "b");
    hg("commit -m \"modify A.txt in branchB\"");
    echo("A.txt", "bbb");
    hg("commit -m \"modify A.txt in branchB\"");
    hg("merge branchA");
    verifyMergeData(myRepository.findChild("A.txt"), "base", "basebbbb", "baseaaaa");
  }

  private void verifyMergeData(final VirtualFile file, String expectedBase, String expectedLocal, String expectedServer) {
    try {
      final MergeData mergeData = myMergeProvider.loadRevisions(file);
      assertEquals(expectedLocal, new String(mergeData.CURRENT));
      assertEquals(expectedServer, new String(mergeData.LAST));
      assertEquals(expectedBase, new String(mergeData.ORIGINAL));
    }
    catch (VcsException e) {
      fail("Failed to load revisions");
    }
  }
}
