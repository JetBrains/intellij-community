// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus.*
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.VfsTestUtil.createDir
import com.intellij.ui.GuiUtils
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.add

class GitChangeProviderVersionedTest : GitChangeProviderTest() {

  fun testCreateFile() {
    val file = create(projectRoot, "new.txt")
    repo.add(file.path)
    assertChanges(file, ADDED)
  }

  fun testCreateFileInDir() {
    val dir = createDir(projectRoot, "newdir")
    dirty(dir)
    val bfile = create(dir, "new.txt")
    repo.add(bfile.path)
    assertChanges(listOf(bfile, dir), listOf(ADDED, null))
  }

  fun testEditFile() {
    edit(atxt, "new content")
    assertChanges(atxt, MODIFIED)
  }

  fun testDeleteFile() {
    deleteFile(atxt)
    assertChanges(atxt, DELETED)
  }

  fun testDeleteDirRecursively() {
    GuiUtils.runOrInvokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        val dir = projectRoot.findChild("dir")!!
        dirtyScope.addDirtyDirRecursively(VcsUtil.getFilePath(dir))
        FileUtil.delete(VfsUtilCore.virtualToIoFile(dir))
      }
    }
    assertChanges(listOf(dir_ctxt, subdir_dtxt),
                  listOf(DELETED, DELETED))
  }

  fun testMoveNewFile() {
    // IDEA-59587
    // Reproducibility of the bug (in the original roots cause) depends on the order of new and old paths in the dirty scope.
    // MockDirtyScope shouldn't preserve the order of items added there - a Set is returned from getDirtyFiles().
    // But the order is likely preserved if it meets the natural order of the items inserted into the dirty scope.
    // That's why the test moves from .../repo/dir/new.txt to .../repo/new.txt - to make the old path appear later than the new one.
    // This is not consistent though.
    val dir = projectRoot.findChild("dir")!!
    val file = create(dir, "new.txt")
    moveFile(file, projectRoot)
    assertChanges(file, ADDED)
  }

  fun testSimultaneousOperationsOnMultipleFiles() {
    edit(atxt, "new afile content")
    edit(dir_ctxt, "new cfile content")
    deleteFile(subdir_dtxt)
    val newfile = create(projectRoot, "newfile.txt")
    repo.add()

    assertChanges(listOf(atxt, dir_ctxt, subdir_dtxt, newfile), listOf(MODIFIED, MODIFIED, DELETED, ADDED))
  }

}
