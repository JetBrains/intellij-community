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
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsAppendableDirtyScope;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.changes.pending.MockChangeListManagerGate;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeProvider;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.FileStatus.ADDED;
import static com.intellij.openapi.vcs.FileStatus.MODIFIED;
import static com.intellij.openapi.vcs.FileStatus.DELETED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope (better to use VcsDirtyScopeManagerImpl, but it's too asynchronous - couldn't overcome this for now.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 * TODO: change VirtualFile to FilePath or path declared by String - and add tests on move etc. Otherwise VirtualFile is not fuctional for move and harder to test deletes.
 * TODO: there is almost nothing special about git. Expand to all version controls. But beware that tests should be modified on VCs which track directories.
 * @author Kirill Likhodedov
 */
public class GitChangeProviderTest extends GitTestCase {

  private GitChangeProvider myChangeProvider;
  private VcsAppendableDirtyScope myDirtyScope;
  private Map<String, VirtualFile> myFiles;
  private VirtualFile afile;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myChangeProvider = (GitChangeProvider) GitVcs.getInstance(myProject).getChangeProvider();
    myDirtyScope = new VcsDirtyScopeImpl(GitVcs.getInstance(myProject), myProject);

    myFiles = createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    afile = myFiles.get("a.txt"); // the file is commonly used, so save it in a field.
    myRepo.commit();
  }

  @Test
  public void testCreateFile() throws Exception {
    VirtualFile bfile = myRepo.createFile("new.txt");
    assertChanges(bfile, ADDED);
  }

  @Test
  public void testCreateFileInDir() throws Exception {
    VirtualFile dir = createDirInCommand(myRepo.getDir(), "newdir");
    VirtualFile bfile = createFileInCommand(dir, "new.txt", "initial b");
    assertChanges(new VirtualFile[] {bfile, dir}, new FileStatus[] { ADDED, null} );
  }

  @Test
  public void testEditFile() throws Exception {
    editFileInCommand(afile, "new content");
    assertChanges(afile, MODIFIED);
  }

  @Test
  public void testDeleteFile() throws Exception {
    deleteFileInCommand(afile);
    assertChanges(afile, DELETED);
  }

  @Test
  public void testDeleteDirRecursively() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override public void run() {
        FileUtil.delete(new File(myRepo.getDir().getPath(), "dir"));
      }
    });
    assertChanges(new VirtualFile[] { myFiles.get("dir/c.txt"), myFiles.get("dir/subdir/d.txt") }, new FileStatus[] { DELETED, DELETED });
  }

  @Test
  public void testSimultaneousOperationsOnMultipleFiles() throws Exception {
    VirtualFile dfile = myFiles.get("dir/subdir/d.txt");
    VirtualFile cfile = myFiles.get("dir/c.txt");

    editFileInCommand(afile, "new content");
    editFileInCommand(cfile, "new content");
    deleteFileInCommand(dfile);
    VirtualFile newfile = createFileInCommand("newfile.txt", "new content");

    assertChanges(new VirtualFile[] {afile, cfile, dfile, newfile}, new FileStatus[] {MODIFIED, MODIFIED, DELETED, ADDED});
  }

  /**
   * Checks that the given files have respective statuses in the change list retrieved from myChangesProvider.
   * Pass null in the fileStatuses array to indicate that proper file has not changed.
   */
  private void assertChanges(VirtualFile[] virtualFiles, FileStatus[] fileStatuses) throws VcsException {
    Map<VirtualFile, Change> result = getChanges(virtualFiles);
    for (int i = 0; i < virtualFiles.length; i++) {
      VirtualFile vf = virtualFiles[i];
      FileStatus status = fileStatuses[i];
      if (status == null) {
        assertFalse(result.containsKey(vf), "File [" + vf + " shouldn't be in the change list, but it was.");
        continue;
      }
      assertTrue(result.containsKey(vf), "File [" + vf + "] didn't change. Changes: " + result);
      assertEquals(result.get(vf).getFileStatus(), status, "File statuses don't match for file [" + vf + "]");
    }
  }

  private void assertChanges(VirtualFile virtualFile, FileStatus fileStatus) throws VcsException {
    assertChanges(new VirtualFile[] { virtualFile }, new FileStatus[] { fileStatus });
  }

  /**
   * Marks the given files dirty in myDirtyScope, gets changes from myChangeProvider and groups the changes in the map.
   * Assumes that only one change for a file happened.
   */
  private Map<VirtualFile, Change> getChanges(VirtualFile... changedFiles) throws VcsException {
    // populate dirty scope
    for (VirtualFile vf : changedFiles) {
      myDirtyScope.addDirtyFile(new FilePathImpl(vf));
    }

    // get changes
    MockChangelistBuilder builder = new MockChangelistBuilder();
    myChangeProvider.getChanges(myDirtyScope, builder, new EmptyProgressIndicator(), new MockChangeListManagerGate(ChangeListManager.getInstance(myProject)));
    List<Change> changes = builder.getChanges();

    // get changes for files
    Map<VirtualFile, Change> result = new HashMap<VirtualFile, Change>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      if (file == null) { // if a file was deleted, just find the reference in the original list of files and use it. 
        String path = change.getBeforeRevision().getFile().getPath();
        for (VirtualFile vf : changedFiles) {
          if (vf.getPath().equals(path)) {
            file = vf;
            break;
          }
        }
      }
      result.put(file, change);
    }
    return result;
  }

  /**
   * <p>Creates file structure for given paths. Path element should be a relative (from project root)
   * path to a file or a directory. All intermediate paths will be created if needed.
   * To create a dir without creating a file pass "dir/" as a parameter.</p>
   * <p>Usage example:
   * <code>createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt", "anotherdir/");</code></p>
   * <p>This will create files a.txt and b.txt in the project dir, create directories dir, dir/subdir and anotherdir,
   * and create file c.txt in dir and d.txt in dir/subdir.</p>
   * <p>Note: use forward slash to denote directories, even if it is backslash that separates dirs in your system.</p>
   * <p>All files are populated with "initial content" string.</p>
   */
  private Map<String, VirtualFile> createFileStructure(String... paths) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();

    for (String path : paths) {
      String[] pathElements = path.split("/");
      boolean lastIsDir = path.endsWith("/");
      VirtualFile currentParent = myRepo.getDir();
      for (int i = 0; i < pathElements.length-1; i++) {
        currentParent = createDirInCommand(currentParent, pathElements[i]);
      }

      String lastElement = pathElements[pathElements.length-1];
      currentParent = lastIsDir ? createDirInCommand(currentParent, lastElement) : createFileInCommand(currentParent, lastElement, "initial content");
      result.put(path, currentParent);
    }

    return result;
  }

}
