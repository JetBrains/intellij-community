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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.vcsUtil.VcsUtil;

import static com.intellij.openapi.vcs.FileStatus.*;
import static git4idea.test.GitExecutor.add;

public class GitChangeProviderVersionedTest extends GitChangeProviderTest {

  public void testCreateFile() throws Exception {
    VirtualFile file = create(myRootDir, "new.txt");
    add(file.getPath());
    assertChanges(file, ADDED);
  }

  public void testCreateFileInDir() throws Exception {
    VirtualFile dir = createDir(myRootDir, "newdir");
    dirty(dir);
    VirtualFile bfile = create(dir, "new.txt");
    add(bfile.getPath());
    assertChanges(new VirtualFile[] {bfile, dir}, new FileStatus[] { ADDED, null} );
  }

  public void testEditFile() throws Exception {
    edit(atxt, "new content");
    assertChanges(atxt, MODIFIED);
  }

  public void testDeleteFile() throws Exception {
    deleteFile(atxt);
    assertChanges(atxt, DELETED);
  }

  public void testDeleteDirRecursively() throws Exception {
    GuiUtils.runOrInvokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      final VirtualFile dir = myProjectRoot.findChild("dir");
      myDirtyScope.addDirtyDirRecursively(VcsUtil.getFilePath(dir));
      FileUtil.delete(VfsUtilCore.virtualToIoFile(dir));
    }));
    assertChanges(new VirtualFile[] { dir_ctxt, subdir_dtxt },
                  new FileStatus[] { DELETED, DELETED });
  }

  public void testMoveNewFile() throws Exception {
    // IDEA-59587
    // Reproducibility of the bug (in the original roots cause) depends on the order of new and old paths in the dirty scope.
    // MockDirtyScope shouldn't preserve the order of items added there - a Set is returned from getDirtyFiles().
    // But the order is likely preserved if it meets the natural order of the items inserted into the dirty scope.
    // That's why the test moves from .../repo/dir/new.txt to .../repo/new.txt - to make the old path appear later than the new one.
    // This is not consistent though.
    final VirtualFile dir= myProjectRoot.findChild("dir");
    final VirtualFile file = create(dir, "new.txt");
    moveFile(file, myRootDir);
    assertChanges(file, ADDED);
  }

  public void testSimultaneousOperationsOnMultipleFiles() throws Exception {
    edit(atxt, "new afile content");
    edit(dir_ctxt, "new cfile content");
    deleteFile(subdir_dtxt);
    VirtualFile newfile = create(myRootDir, "newfile.txt");
    add();

    assertChanges(new VirtualFile[] {atxt, dir_ctxt, subdir_dtxt, newfile}, new FileStatus[] {MODIFIED, MODIFIED, DELETED, ADDED});
  }

}
