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
package hg4idea.test.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.log.HgHistoryUtil;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgLogHistoryTest extends HgPlatformTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testContainedInBranchesInLogInfos() throws VcsException {
    createBookmarksAndBranches(myRepository);
    Hash testHashForFirstCommit = HashImpl.build("0");
    Collection<String> branches = HgHistoryUtil.getDescendingHeadsOfBranches(myProject, myRepository, testHashForFirstCommit);
    //B_Bookmark should not be listed - it is inactive and not head//
    VcsTestUtil.assertEqualCollections(branches, Arrays.asList("default", "branchA", "branchB", "A_BookMark", "C_BookMark"));
  }

  private static void createBookmarksAndBranches(@NotNull VirtualFile repositoryRoot) {
    cd(repositoryRoot);
    hg("bookmark A_BookMark");
    String aFile = "A.txt";
    touch(aFile, "base");
    hg("add " + aFile);
    hg("commit -m 'create file'");
    hg("branch branchA");
    echo(aFile, " modify with a");
    hg("commit -m 'create branchA'");
    hg("bookmark B_BookMark --inactive");
    echo(aFile, " modify with AA");
    hg("commit -m 'modify branchA'");
    hg("up default");
    hg("branch branchB");
    echo(aFile, " modify with b");
    hg("commit -m 'modify file in branchB'");
    hg("bookmark C_BookMark");
    hg("up branchA");
  }
}
