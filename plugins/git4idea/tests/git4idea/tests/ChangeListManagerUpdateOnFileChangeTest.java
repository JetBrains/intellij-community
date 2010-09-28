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

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the ChangeListManager gets notified about changes in externally modified files: created, edited, deleted, moved, renamed, etc. 
 * Note that Changes themselves are not tested here. Only affected files are.
 *
 * TODO: better to test that VcsDirtyScopeManagerImpl adds the files to the dirty scope, but due to multiple asynchronouses there
 * I couldn't make a suitable test.
 * TODO: This shouldn't rely on Git. Either test all version controls, either test a mock version control. Or even use both approaches.
 * @author Kirill Likhodedov
 */
public class ChangeListManagerUpdateOnFileChangeTest extends GitTestCase {

  private ChangeListManagerImpl myChangeListManager;
  private VirtualFile afile;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    // we'll need a file in all tests => testing file creation here
    afile = createFileInCommand("a.txt", "initial content");
    assertInChangeList(afile.getPath());
    myRepo.commit("commit Message");
    myChangeListManager.ensureUpToDate(false);
  }

  @Test
  public void testEditFile() throws IOException {
    editFileInCommand(myProject, afile, "new content");
    assertInChangeList(afile.getPath());
  }

  @Test
  public void testDeleteFile() throws IOException {
    myRepo.commit("commit Message");
    deleteFileInCommand(afile);
    assertInChangeList(afile.getPath());
  }

  @Test
  public void testRenameFile() throws IOException {
    String oldpath = afile.getPath();
    myRepo.commit("commit Message");
    renameFileInCommand(afile, "anew.txt");
    assertInChangeList(oldpath, afile.getPath());
  }

  @Test
  public void testMoveFile() throws IOException {
    String oldpath = afile.getPath();
    VirtualFile dir = createDirInCommand(myRepo.getDir(), "dir");
    moveFileInCommand(afile, dir);
    assertInChangeList(oldpath, afile.getPath());
  }

  @Test
  public void testCopyFile() throws IOException {
    VirtualFile acopy = copyFileInCommand(afile, "acopy.txt");
    assertInChangeList(acopy.getPath());
  }

  private void assertInChangeList(String... filepaths) {
    myChangeListManager.ensureUpToDate(false);

    // ChangeListManager.getAffectedFiles may return duplicates
    final Set<File> dirtyFiles = new HashSet<File>(myChangeListManager.getAffectedPaths());
    for (String filepath : filepaths) {
      assertTrue(dirtyFiles.contains(new File(filepath)), "File " + filepath + " not in the change list manager: " + dirtyFiles);
    }
    assertEquals(dirtyFiles.size(), filepaths.length, "More files in the change list manager: " + dirtyFiles);
  }

}
