/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.util.ArrayUtil;
import git4idea.GitVcs;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.add;
import static git4idea.test.GitExecutor.addCommit;
import static git4idea.test.GitExecutor.git;

/**
 * Tests unversioned files tracking by the {@link git4idea.status.GitChangeProvider}.
 * This test is separate from {@link GitChangeProviderTest}, because in the {@link git4idea.status.GitNewChangesCollector} 
 * untracked files are handled separately by the {@link git4idea.repo.GitUntrackedFilesHolder}.
 */
public class GitChangeProviderUnversionedTest extends GitChangeProviderTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void test_create_but_not_add_via_IDEA_makes_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned(file);
  }

  public void disabled_test_add_revert_makes_unversioned() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    final List<Change> changes = new ArrayList<Change>(getChanges(file).values());
    assertEquals(changes.size(), 1);
    assertEquals(changes.iterator().next().getVirtualFile(), file);
    RollbackEnvironment re = myVcs.getRollbackEnvironment();
    assertNotNull(re);
    re.rollbackChanges(changes, new ArrayList<VcsException>(), new RollbackProgressModifier(0, new EmptyProgressIndicator()));
    assertUnversioned(file);
  }

  public void test_create_outside_makes_unversioned() throws Exception {
    touch("unversioned.txt", "content");
    refresh();
    VirtualFile vf = myRootDir.findChild("unversioned.txt");
    assertTrue(vf.exists());
    myDirtyScope.addDirtyFile(new FilePathImpl(vf));
    assertUnversioned(vf);
  }
  
  // IDEA-73996
  public void test_create_folder_outside_makes_files_of_this_folder_unversioned() throws Exception {
    touch("newdir/newfile.txt");
    refresh();
    VirtualFile dir = myRootDir.findChild("newdir");
    assertTrue(dir.exists());
    VirtualFile vf = dir.findChild("newfile.txt");
    assertTrue(vf.exists());
    myDirtyScope.addDirtyDirRecursively(new FilePathImpl(dir));
    assertUnversioned(vf);
  }
  
  public void test_rm_from_outside_makes_unversioned() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    addCommit("adding new.txt");
    git("rm --cached new.txt");
    refresh();
    assertUnversioned(file);
  }
  
  public void test_add_from_outside_makes_versioned() throws Exception {
    prohibitAutoAdd();
    create(myRootDir, "new.txt");
    add("new.txt");
    refresh();
    assertUnversioned();
  }

  public void test_copy_versioned_makes_destination_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile newFile = copy(atxt, mySubDir);
    assertUnversioned(newFile);
  }

  public void disabled_test_copy_unversioned_makes_destination_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned(file);
    VirtualFile newFile = copy(file, mySubDir);
    assertUnversioned(file, newFile);
  }

  public void test_move_versioned_makes_destination_versioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile dir = myRootDir.findChild("dir");
    move(atxt, dir);
    assertUnversioned();
  }

  public void disabled_test_move_unversioned_makes_destination_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned(file);
    move(file, mySubDir);
    assertUnversioned(file);
  }

  private void assertUnversioned(VirtualFile... files) throws VcsException {
    MockChangelistBuilder builder = new MockChangelistBuilder();
    myChangeProvider.getChanges(myDirtyScope, builder, new EmptyProgressIndicator(),
                                new MockChangeListManagerGate(ChangeListManager.getInstance(myProject)));
    List<VirtualFile> unversionedFiles = builder.getUnversionedFiles();
    
    assertEquals("Incorrect number of unversioned files: " + unversionedFiles, files.length, unversionedFiles.size());
    for (VirtualFile expected : files) {
      if (!unversionedFiles.contains(expected)) {
        fail("Expected file " + expected + " is not unversioned!");
      }
    }
    for (VirtualFile actual : unversionedFiles) {
      if (ArrayUtil.find(files, actual) < 0) {
        fail("Actually unversioned file " + actual + " is not in the unversioned list!");
      }
    }
  }

  private void prohibitAutoAdd() {
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, VcsConfiguration.StandardConfirmation.ADD,
                                                VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

}
