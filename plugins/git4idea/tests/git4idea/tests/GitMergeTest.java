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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import git4idea.test.GitTest;
import git4idea.test.GitTestUtil;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertTrue;

/**
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitMergeTest extends GitTest {

  /**
   * Tests that merge commit after resolving a conflict works fine if there is a file with spaces in its path.
   * IDEA-50318
   */
  @Test
  public void testMergeCommitWithSpacesInPath() throws IOException {
    final String PATH = "dir with spaces/file with spaces.txt";
    GitTestUtil.createFileStructure(myProject, myRepo, PATH);
    myRepo.commit();
    myRepo.push("origin", "master");
    editFileInCommand(myRepo.getVFRootDir().findFileByRelativePath(PATH), "my content");
    myRepo.addCommit();

    myBrotherRepo.pull();
    editFileInCommand(myBrotherRepo.getVFRootDir().findFileByRelativePath(PATH), "brother content");
    myBrotherRepo.addCommit();
    myBrotherRepo.push();

    myRepo.pull();
    editFileInCommand(myRepo.getVFRootDir().findFileByRelativePath(PATH), "my and brother content"); // manually resolving conflict
    myRepo.add();

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);
    final LocalChangeList changeList = changeListManager.getDefaultChangeList();
    changeList.setName("Name");
    changeList.setComment("Commit message");
    final AtomicBoolean res = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        res.set(changeListManager.commitChangesSynchronouslyWithResult(changeList, new ArrayList<Change>(
          changeListManager.getChangesIn(myRepo.getVFRootDir()))));
      }
    }, ModalityState.defaultModalityState());
    assertTrue(res.get());
    changeListManager.ensureUpToDate(false);
    assertTrue(changeListManager.getChangesIn(myRepo.getVFRootDir()).isEmpty());
  }

}
