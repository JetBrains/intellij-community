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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.MockDirtyScope;
import git4idea.GitVcs;
import git4idea.status.GitChangeProvider;
import git4idea.test.GitTest;
import git4idea.test.GitTestUtil;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitChangeProviderTest extends GitTest {

  protected GitChangeProvider myChangeProvider;
  protected VcsModifiableDirtyScope myDirtyScope;
  protected Map<String, VirtualFile> myFiles;
  protected VirtualFile afile;
  protected VirtualFile myRootDir;
  protected VirtualFile mySubDir;

  @BeforeMethod
  @Override
  protected void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
    myChangeProvider = (GitChangeProvider) GitVcs.getInstance(myProject).getChangeProvider();

    myFiles = GitTestUtil.createFileStructure(myProject, myRepo, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    myRepo.addCommit();
    myRepo.refresh();

    afile = myFiles.get("a.txt"); // the file is commonly used, so save it in a field.
    myRootDir = myRepo.getVFRootDir();
    mySubDir = myRootDir.findChild("dir");

    myDirtyScope = new MockDirtyScope(myProject, GitVcs.getInstance(myProject));
  }

  protected void modifyFileInBranches(String filename, FileAction masterAction, FileAction featureAction) throws Exception {
    myRepo.createBranch("feature");
    performActionOnFileAndRecordToIndex(filename, "feature", featureAction);
    myRepo.commit();
    myRepo.checkout("master");
    performActionOnFileAndRecordToIndex(filename, "master", masterAction);
    myRepo.commit();
    myRepo.merge("feature");
    myRepo.refresh();
  }

  protected enum FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private void performActionOnFileAndRecordToIndex(String filename, String branchName, FileAction action) throws Exception {
    VirtualFile file = myRepo.getVFRootDir().findChild(filename);
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
        myRootDir.refresh(false, true);
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
  protected void assertChanges(VirtualFile[] virtualFiles, FileStatus[] fileStatuses) throws VcsException {
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

  protected void assertChanges(VirtualFile virtualFile, FileStatus fileStatus) throws VcsException {
    assertChanges(new VirtualFile[] { virtualFile }, new FileStatus[] { fileStatus });
  }

  /**
   * Marks the given files dirty in myDirtyScope, gets changes from myChangeProvider and groups the changes in the map.
   * Assumes that only one change for a file has happened.
   */
  protected Map<FilePath, Change> getChanges(VirtualFile... changedFiles) throws VcsException {
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

  protected VirtualFile create(VirtualFile parent, String name) {
    return create(parent, name, false);
  }

  protected VirtualFile createDir(VirtualFile parent, String name) {
    return create(parent, name, true);
  }

  private VirtualFile create(VirtualFile parent, String name, boolean dir) {
    final VirtualFile file = dir ? createDirInCommand(parent, name) : createFileInCommand(parent, name, "content" + Math.random());
    dirty(file);
    return file;
  }

  protected void edit(VirtualFile file, String content) {
    editFileInCommand(file, content);
    dirty(file);
  }

  protected void move(VirtualFile file, VirtualFile newParent) {
    dirty(file);
    moveFileInCommand(file, newParent);
    dirty(file);
  }

  protected VirtualFile copy(VirtualFile file, VirtualFile newParent) {
    dirty(file);
    VirtualFile newFile = copyFileInCommand(file, newParent);
    dirty(newFile);
    return newFile;
  }

  protected void delete(VirtualFile file) {
    dirty(file);
    deleteFileInCommand(file);
  }

  private void dirty(VirtualFile file) {
    myDirtyScope.addDirtyFile(new FilePathImpl(file));
  }
}
