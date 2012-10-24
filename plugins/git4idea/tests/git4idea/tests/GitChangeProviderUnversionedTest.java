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
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.util.ArrayUtil;
import git4idea.GitVcs;
import git4idea.test.GitTestRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Tests unversioned files tracking by the {@link git4idea.status.GitChangeProvider}.
 * This test is separate from {@link GitChangeProviderTest}, because in the {@link git4idea.status.GitNewChangesCollector} 
 * untracked files are handled separately by the {@link git4idea.repo.GitUntrackedFilesHolder}.
 *
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitChangeProviderUnversionedTest extends GitChangeProviderTest {

  @BeforeMethod
  @Override
  protected void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
  }

  @Test
  public void create_but_not_add_via_IDEA_makes_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned(file);
  }

  @Test
  public void create_add_via_IDEA_makes_versioned() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned();
  }
  
  @Test
  public void add_revert_makes_unversioned() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    final List<Change> changes = new ArrayList<Change>(getChanges(file).values());
    assertEquals(changes.size(), 1);
    assertEquals(changes.iterator().next().getVirtualFile(), file);
    myVcs.getRollbackEnvironment().rollbackChanges(changes, new ArrayList<VcsException>(), new RollbackProgressModifier(0, new EmptyProgressIndicator()));
    assertUnversioned(file);
  }

  @Test
  public void create_outside_makes_unversioned() throws Exception {
    File file = myRepo.createFile("unversioned.txt", "content");
    myRepo.refresh();
    VirtualFile vf = myRootDir.findChild("unversioned.txt");
    assertTrue(vf.exists());
    myDirtyScope.addDirtyFile(new FilePathImpl(vf));
    assertUnversioned(vf);
  }
  
  // IDEA-73996
  @Test
  public void create_folder_outside_makes_files_of_this_folder_unversioned() throws Exception {
    Thread.sleep(1000);
    myRepo.refresh();
    File subdir = myRepo.createDir("newdir");
    File file = GitTestRepository.createFile(subdir, "newfile.txt", "");
    myRepo.refresh();
    VirtualFile dir = myRootDir.findChild("newdir");
    assertTrue(dir.exists());
    VirtualFile vf = dir.findChild("newfile.txt");
    assertTrue(vf.exists());
    myDirtyScope.addDirtyDirRecursively(new FilePathImpl(dir));
    assertUnversioned(vf);
  }
  
  @Test
  public void rm_from_outside_makes_unversioned() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    myRepo.run("rm", "--cached", "new.txt");
    myRepo.refresh();
    assertUnversioned(file);
  }
  
  @Test
  public void add_from_outside_makes_versioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    myRepo.add("new.txt");    
    myRepo.refresh();
    Thread.sleep(1000);
    assertUnversioned();
  }

  @Test
  public void copy_versioned_makes_destination_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile newFile = copy(afile, mySubDir);
    assertUnversioned(newFile);
  }

  @Test
  public void copy_unversioned_makes_destination_unversioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile file = create(myRootDir, "new.txt");
    assertUnversioned(file);
    VirtualFile newFile = copy(file, mySubDir);
    assertUnversioned(file, newFile);
  }

  @Test
  public void move_versioned_makes_destination_versioned() throws Exception {
    prohibitAutoAdd();
    VirtualFile dir = myRootDir.findChild("dir");
    move(afile, dir);
    assertUnversioned();
  }

  @Test
  public void move_unversioned_makes_destination_unversioned() throws Exception {
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
    
    assertEquals(unversionedFiles.size(), files.length, "Incorrect number of unversioned files.");
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
    setStandardConfirmation(GitVcs.NAME, VcsConfiguration.StandardConfirmation.ADD, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

}
