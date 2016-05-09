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
package git4idea.tests;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.overwrite;
import static git4idea.test.GitExecutor.*;

public class GitCommitTest extends GitSingleRepoTest {
  private static final Logger LOG = Logger.getInstance(GitCommitTest.class);

  /**
   * Tests that merge commit after resolving a conflict works fine if there is a file with spaces in its path.
   * IDEA-50318
   */
  @Test
  public void testMergeCommitWithSpacesInPath() throws IOException {
    final String PATH = "dir with spaces/file with spaces.txt";
    GitTestUtil.createFileStructure(myProjectRoot, PATH);
    addCommit("created some file structure");

    git("branch feature");

    File file = new File(myProjectPath, PATH);
    assertTrue("File doesn't exist!", file.exists());
    overwrite(file, "my content");
    addCommit("modified in master");

    checkout("feature");
    overwrite(file, "brother content");
    addCommit("modified in feature");

    checkout("master");
    git("merge feature", true); // ignoring non-zero exit-code reporting about conflicts
    overwrite(file, "merged content"); // manually resolving conflict
    git("add .");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    updateChangeListManager();
    final LocalChangeList changeList = changeListManager.getDefaultChangeList();
    changeList.setComment("Commit message");
    List<Change> changes = ContainerUtil.newArrayList(changeListManager.getChangesIn(myProjectRoot));
    assertTrue(!changes.isEmpty());

    CheckinEnvironment checkingEnv = ObjectUtils.assertNotNull(myVcs.getCheckinEnvironment());
    List<VcsException> exceptions = checkingEnv.commit(changes, "comment");
    assertNoExceptions(exceptions);

    updateChangeListManager();
    assertTrue(changeListManager.getChangesIn(myProjectRoot).isEmpty());
  }

  private static void assertNoExceptions(@Nullable List<VcsException> exceptions) {
    VcsException ex = ContainerUtil.getFirstItem(exceptions);
    if (ex != null) {
      LOG.error(ex);
      fail("Exception during executing the commit: " + ex.getMessage());
    }
  }
}
