// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus.*
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.VfsTestUtil.createDir
import com.intellij.testFramework.runInEdtAndGet
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
    val dir = runInEdtAndGet { createDir(projectRoot, "newdir") }
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

  fun testSimultaneousOperationsOnMultipleFiles() {
    edit(atxt, "new afile content")
    edit(dir_ctxt, "new cfile content")
    deleteFile(subdir_dtxt)
    val newfile = create(projectRoot, "newfile.txt")
    repo.add()

    assertChanges(listOf(atxt, dir_ctxt, subdir_dtxt, newfile), listOf(MODIFIED, MODIFIED, DELETED, ADDED))
  }

}
