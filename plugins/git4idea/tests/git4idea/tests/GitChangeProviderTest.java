/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.MockDirtyScope;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitVcs;
import git4idea.status.GitChangeProvider;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.Executor.touch;
import static com.intellij.openapi.vcs.VcsTestUtil.*;
import static git4idea.test.GitExecutor.*;

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 */
public abstract class GitChangeProviderTest extends GitSingleRepoTest {

  protected GitChangeProvider myChangeProvider;
  protected VcsModifiableDirtyScope myDirtyScope;
  protected VirtualFile myRootDir;
  protected VirtualFile mySubDir;
  protected GitVcs myVcs;

  protected VirtualFile atxt;
  protected VirtualFile btxt;
  protected VirtualFile dir_ctxt;
  protected VirtualFile subdir_dtxt;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    try {
      initTest();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }
  }

  private void initTest() {
    myVcs = GitVcs.getInstance(myProject);
    assertNotNull(myVcs);
    myChangeProvider = (GitChangeProvider) myVcs.getChangeProvider();

    GitTestUtil.createFileStructure(myProjectRoot, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    addCommit("initial");

    atxt = getVirtualFile("a.txt");
    btxt = getVirtualFile("b.txt");
    dir_ctxt = getVirtualFile("dir/c.txt");
    subdir_dtxt = getVirtualFile("dir/subdir/d.txt");

    myRootDir = myProjectRoot;
    mySubDir = myRootDir.findChild("dir");

    myDirtyScope = new MockDirtyScope(myProject, myVcs);

    cd(myProjectPath);
  }

  @Override
  protected boolean makeInitialCommit() {
    return false;
  }

  @Nullable
  private VirtualFile getVirtualFile(@NotNull String relativePath) {
    return VfsUtil.findFileByIoFile(new File(myProjectPath, relativePath), true);
  }

  protected void modifyFileInBranches(String filename, FileAction masterAction, FileAction featureAction) throws Exception {
    git("checkout -b feature");
    performActionOnFileAndRecordToIndex(filename, "feature", featureAction);
    commit("commit to feature");
    checkout("master");
    refresh();
    performActionOnFileAndRecordToIndex(filename, "master", masterAction);
    commit("commit to master");
    git("merge feature", true);
    refresh();
  }

  protected enum FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private void performActionOnFileAndRecordToIndex(String filename, String branchName, FileAction action) throws Exception {
    VirtualFile file = myRootDir.findChild(filename);
    if (action != FileAction.CREATE) { // file doesn't exist yet
      assertNotNull("VirtualFile is null: " + filename, file);
    }
    switch (action) {
      case CREATE:
        File f = touch(filename, "initial content in branch " + branchName);
        final VirtualFile createdFile = VfsUtil.findFileByIoFile(f, true);
        dirty(createdFile);
        add(filename);
        break;
      case MODIFY:
        //noinspection ConstantConditions
        overwrite(VfsUtilCore.virtualToIoFile(file), "new content in branch " + branchName);
        dirty(file);
        add(filename);
        break;
      case DELETE:
        dirty(file);
        git("rm " + filename);
        break;
      case RENAME:
        String newName = filename + "_" + branchName.replaceAll("\\s", "_") + "_new";
        dirty(file);
        mv(filename, newName);
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
      FilePath fp = VcsUtil.getFilePath(virtualFiles[i]);
      FileStatus status = fileStatuses[i];
      if (status == null) {
        assertFalse("File [" + tos(fp) + " shouldn't be in the changelist, but it was.", result.containsKey(fp));
        continue;
      }
      assertTrue("File [" + tos(fp) + "] didn't change. Changes: " + tos(result), result.containsKey(fp));
      assertEquals("File statuses don't match for file [" + tos(fp) + "]", result.get(fp).getFileStatus(), status);
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
    final Map<FilePath, Change> result = new HashMap<>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      FilePath filePath = null;
      if (file == null) { // if a file was deleted, just find the reference in the original list of files and use it. 
        String path = change.getBeforeRevision().getFile().getPath();
        for (FilePath fp : changedPaths) {
          if (FileUtil.pathsEqual(fp.getPath(), path)) {
            filePath = fp;
            break;
          }
        }
      } else {
        filePath = VcsUtil.getFilePath(file);
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
    final VirtualFile file = dir ?
                             VcsTestUtil.findOrCreateDir(myProject, parent, name) :
                             createFile(myProject, parent, name, "content" + Math.random());
    dirty(file);
    return file;
  }

  protected void edit(VirtualFile file, String content) {
    editFileInCommand(myProject, file, content);
    dirty(file);
  }

  protected void moveFile(VirtualFile file, VirtualFile newParent) {
    dirty(file);
    moveFileInCommand(myProject, file, newParent);
    dirty(file);
  }

  protected VirtualFile copy(VirtualFile file, VirtualFile newParent) {
    dirty(file);
    VirtualFile newFile = copyFileInCommand(myProject, file, newParent, file.getName());
    dirty(newFile);
    return newFile;
  }

  protected void deleteFile(VirtualFile file) {
    dirty(file);
    deleteFileInCommand(myProject, file);
  }

  private void dirty(VirtualFile file) {
    myDirtyScope.addDirtyFile(VcsUtil.getFilePath(file));
  }

  protected String tos(FilePath fp) {
    return FileUtil.getRelativePath(new File(myProjectPath), fp.getIOFile());
  }

  protected String tos(Change change) {
    switch (change.getType()) {
      case NEW: return "A: " + tos(change.getAfterRevision());
      case DELETED: return "D: " + tos(change.getBeforeRevision());
      case MOVED: return "M: " + tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
      case MODIFICATION: return "M: " + tos(change.getAfterRevision());
      default: return "~: " +  tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
    }
  }

  protected String tos(ContentRevision revision) {
    return tos(revision.getFile());
  }

  protected String tos(Map<FilePath, Change> changes) {
    StringBuilder stringBuilder = new StringBuilder("[");
    for (Change change : changes.values()) {
      stringBuilder.append(tos(change)).append(", ");
    }
    stringBuilder.append("]");
    return stringBuilder.toString();
  }

}
