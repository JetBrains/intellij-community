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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vcs.changes.pending.MockChangeListManagerGate;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.MockDirtyScope;
import com.intellij.ui.GuiUtils;
import git4idea.GitVcs;
import git4idea.changes.GitChangeProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.FileStatus.*;
import static org.testng.Assert.*;

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 * @author Kirill Likhodedov
 */
public class GitChangeProviderTest extends GitSingleUserTest {

  private GitChangeProvider myChangeProvider;
  private VcsModifiableDirtyScope myDirtyScope;
  private Map<String, VirtualFile> myFiles;
  private VirtualFile afile;
  private VirtualFile myRootDir;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myChangeProvider = (GitChangeProvider) GitVcs.getInstance(myProject).getChangeProvider();

    myFiles = GitTestUtil.createFileStructure(myProject, myRepo, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    myRepo.addCommit();
    myRepo.refresh();

    afile = myFiles.get("a.txt"); // the file is commonly used, so save it in a field.
    myRootDir = myRepo.getDir();

    myDirtyScope = new MockDirtyScope(myProject, GitVcs.getInstance(myProject));
  }

  @Test
  public void testCreateFile() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    assertChanges(file, ADDED);
  }

  @Test
  public void testCreateFileInDir() throws Exception {
    VirtualFile dir = createDir(myRootDir, "newdir");
    VirtualFile bfile = create(dir, "new.txt");
    assertChanges(new VirtualFile[] {bfile, dir}, new FileStatus[] { ADDED, null} );
  }

  @Test
  public void testEditFile() throws Exception {
    edit(afile, "new content");
    assertChanges(afile, MODIFIED);
  }

  @Test
  public void testDeleteFile() throws Exception {
    delete(afile);
    assertChanges(afile, DELETED);
  }

  @Test
  public void testDeleteDirRecursively() throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final VirtualFile dir= myRepo.getDir().findChild("dir");
            myDirtyScope.addDirtyDirRecursively(new FilePathImpl(dir));
            FileUtil.delete(VfsUtil.virtualToIoFile(dir));
          }
        });
      }
    });
    assertChanges(new VirtualFile[] { myFiles.get("dir/c.txt"), myFiles.get("dir/subdir/d.txt") }, new FileStatus[] { DELETED, DELETED });
  }

  @Test
  public void testMoveNewFile() throws Exception {
    // IDEA-59587
    // Reproducibility of the bug (in the original roots cause) depends on the order of new and old paths in the dirty scope.
    // MockDirtyScope shouldn't preserve the order of items added there - a Set is returned from getDirtyFiles().
    // But the order is likely preserved if it meets the natural order of the items inserted into the dirty scope.
    // That's why the test moves from .../repo/dir/new.txt to .../repo/new.txt - to make the old path appear later than the new one.
    // This is not consistent though.
    final VirtualFile dir= myRepo.getDir().findChild("dir");
    final VirtualFile file = create(dir, "new.txt");
    move(file, myRootDir);
    assertChanges(file, ADDED);
  }

  @Test
  public void testSimultaneousOperationsOnMultipleFiles() throws Exception {
    VirtualFile dfile = myFiles.get("dir/subdir/d.txt");
    VirtualFile cfile = myFiles.get("dir/c.txt");

    edit(afile, "new afile content");
    edit(cfile, "new cfile content");
    delete(dfile);
    VirtualFile newfile = create(myRootDir, "newfile.txt");

    assertChanges(new VirtualFile[] {afile, cfile, dfile, newfile}, new FileStatus[] {MODIFIED, MODIFIED, DELETED, ADDED});
  }

  /**
   * "modify-modify" merge conflict.
   * 1. Create a file and commit it.
   * 2. Create new branch and switch to it.
   * 3. Edit the file in that branch and commit.
   * 4. Switch to master, conflictly edit the file and commit.
   * 5. Merge the branch on master.
   * Merge conflict "modify-modify" happens.
   */
  @Test
  public void testConflictMM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.MODIFY);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Modify-Delete conflict.
   */
  @Test
  public void testConflictMD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.DELETE);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Delete-Modify conflict.
   */
  @Test
  public void testConflictDM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.MODIFY);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Create a file with conflicting content.
   */
  @Test
  public void testConflictCC() throws Exception {
    modifyFileInBranches("z.txt", FileAction.CREATE, FileAction.CREATE);
    VirtualFile zfile = myRepo.getDir().findChild("z.txt");
    assertChanges(zfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  @Test
  public void testConflictRD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.DELETE);
    VirtualFile newfile = myRepo.getDir().findChild("a.txt_master_new"); // renamed in master
    assertChanges(newfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  @Test
  public void testConflictDR() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.RENAME);
    VirtualFile newFile = myRepo.getDir().findChild("a.txt_feature_new"); // deleted in master, renamed in feature
    assertChanges(newFile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  private void modifyFileInBranches(String filename, FileAction masterAction, FileAction featureAction) throws Exception {
    myRepo.createBranch("feature");
    performActionOnFileAndRecordToIndex(filename, "feature", featureAction);
    myRepo.commit();
    myRepo.checkout("master");
    performActionOnFileAndRecordToIndex(filename, "master", masterAction);
    myRepo.commit();
    myRepo.merge("feature");
    myRepo.refresh();
  }

  private enum FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private void performActionOnFileAndRecordToIndex(String filename, String branchName, FileAction action) throws Exception {
    VirtualFile file = myRepo.getDir().findChild(filename);
    switch (action) {
      case CREATE:
        final VirtualFile createdFile = createFileInCommand(filename, "initial content in branch " + branchName);
        dirty(createdFile);
        myRepo.add(filename);
        break;
      case MODIFY:
        editFileInCommand(file, "new content in branch " + branchName);
        dirty(file);
        myRepo.add(filename);
        break;
      case DELETE:
        dirty(file);
        myRepo.rm(filename);
        break;
      case RENAME:
        String newName = filename + "_" + branchName.replaceAll("\\s", "_") + "_new";
        dirty(file);
        myRepo.mv(filename, newName);
        dirty(myRootDir.findChild(newName));
        break;
      default:
        break;
    }
  }

  /**
   * Checks that the given files have respective statuses in the change list retrieved from myChangesProvider.
   * Pass null in the fileStatuses array to indicate that proper file has not changed.
   */
  private void assertChanges(VirtualFile[] virtualFiles, FileStatus[] fileStatuses) throws VcsException {
    Map<FilePath, Change> result = getChanges(virtualFiles);
    for (int i = 0; i < virtualFiles.length; i++) {
      FilePath fp = new FilePathImpl(virtualFiles[i]);
      FileStatus status = fileStatuses[i];
      if (status == null) {
        assertFalse(result.containsKey(fp), "File [" + tos(fp) + " shouldn't be in the change list, but it was.");
        continue;
      }
      assertTrue(result.containsKey(fp), "File [" + tos(fp) + "] didn't change. Changes: " + tos(result));
      assertEquals(result.get(fp).getFileStatus(), status, "File statuses don't match for file [" + tos(fp) + "]");
    }
  }

  private void assertChanges(VirtualFile virtualFile, FileStatus fileStatus) throws VcsException {
    assertChanges(new VirtualFile[] { virtualFile }, new FileStatus[] { fileStatus });
  }

  /**
   * Marks the given files dirty in myDirtyScope, gets changes from myChangeProvider and groups the changes in the map.
   * Assumes that only one change for a file has happened.
   */
  private Map<FilePath, Change> getChanges(VirtualFile... changedFiles) throws VcsException {
    final List<FilePath> changedPaths = ObjectsConvertor.vf2fp(Arrays.asList(changedFiles));

    // get changes
    MockChangelistBuilder builder = new MockChangelistBuilder();
    myChangeProvider.getChanges(myDirtyScope, builder, new EmptyProgressIndicator(), new MockChangeListManagerGate(ChangeListManager.getInstance(myProject)));
    List<Change> changes = builder.getChanges();

    // get changes for files
    final Map<FilePath, Change> result = new HashMap<FilePath, Change>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      FilePath filePath = null;
      if (file == null) { // if a file was deleted, just find the reference in the original list of files and use it. 
        String path = change.getBeforeRevision().getFile().getPath();
        for (FilePath fp : changedPaths) {
          if (fp.getPath().equals(path)) {
            filePath = fp;
            break;
          }
        }
      } else {
        filePath = new FilePathImpl(file);
      }
      result.put(filePath, change);
    }
    return result;
  }

  private VirtualFile create(VirtualFile parent, String name) {
    return create(parent, name, false);
  }

  private VirtualFile createDir(VirtualFile parent, String name) {
    return create(parent, name, true);
  }

  private VirtualFile create(VirtualFile parent, String name, boolean dir) {
    final VirtualFile file = dir ? createDirInCommand(parent, name) : createFileInCommand(parent, name, "content" + Math.random());
    dirty(file);
    return file;
  }

  private void edit(VirtualFile file, String content) {
    editFileInCommand(file, content);
    dirty(file);
  }

  private void move(VirtualFile file, VirtualFile newParent) {
    dirty(file);
    moveFileInCommand(file, newParent);
    dirty(file);
  }

  private void delete(VirtualFile file) {
    dirty(file);
    deleteFileInCommand(file);
  }

  private void dirty(VirtualFile file) {
    myDirtyScope.addDirtyFile(new FilePathImpl(file));
  }
}
