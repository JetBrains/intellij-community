// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatus.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.VfsTestUtil.createDir
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.GuiUtils
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.git

class GitChangeProviderVersionedTest : GitChangeProviderTest() {

  fun testCreateFile() {
    val file = create(projectRoot, "new.txt")
    repo.add(file.path)
    assertProviderChanges(file, ADDED)

    assertChanges {
      added("new.txt")
    }
  }

  fun testCreateFileInDir() {
    val dir = runInEdtAndGet { createDir(projectRoot, "newdir") }
    dirty(dir)
    val bfile = create(dir, "new.txt")
    repo.add(bfile.path)
    assertProviderChanges(listOf(bfile, dir),
                          listOf(ADDED, null))

    assertChanges {
      added("newdir/new.txt")
    }
  }

  fun testEditFile() {
    edit(atxt, "new content")
    assertProviderChanges(atxt, MODIFIED)

    assertChanges {
      modified("a.txt")
    }
  }

  fun testStagedModification() {
    edit(atxt, "new content")
    repo.add(atxt.path)
    assertProviderChanges(atxt, MODIFIED)

    assertChanges {
      modified("a.txt")
    }
  }

  fun testStagedUnstagedModification() {
    edit(atxt, "new content")
    repo.add(atxt.path)
    edit(atxt, "new contents and some extra")
    assertProviderChanges(atxt, MODIFIED)

    assertChanges {
      modified("a.txt")
    }
  }

  fun testRevertedStagedModification() {
    val oldContent = VfsUtil.loadText(atxt)
    edit(atxt, "new content")
    repo.add(atxt.path)
    edit(atxt, oldContent)
    assertProviderChanges(atxt, null)

    assertChanges {
    }
  }

  fun testRevertedStagedAddition() {
    val file = create(projectRoot, "new.txt")
    repo.add(file.path)
    cd(projectRoot)
    rm("new.txt")
    assertProviderChanges(atxt, null)

    assertChanges {
    }
  }

  fun testDeleteFile() {
    deleteFile(atxt)
    assertProviderChanges(atxt, DELETED)

    assertChanges {
      deleted("a.txt")
    }
  }

  fun testDeleteDirRecursively() {
    GuiUtils.runOrInvokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        val dir = projectRoot.findChild("dir")!!
        dirtyScope.addDirtyDirRecursively(VcsUtil.getFilePath(dir))
        FileUtil.delete(VfsUtilCore.virtualToIoFile(dir))
      }
    }
    assertProviderChanges(listOf(dir_ctxt, subdir_dtxt),
                          listOf(DELETED, DELETED))

    assertChanges {
      deleted("dir/c.txt")
      deleted("dir/subdir/d.txt")
    }
  }

  fun testSimultaneousOperationsOnMultipleFiles() {
    edit(atxt, "new afile content")
    edit(dir_ctxt, "new cfile content")
    deleteFile(subdir_dtxt)
    val newfile = create(projectRoot, "newfile.txt")
    repo.add()

    assertProviderChanges(listOf(atxt, dir_ctxt, subdir_dtxt, newfile),
                          listOf(MODIFIED, MODIFIED, DELETED, ADDED))

    assertChanges {
      modified("a.txt")
      modified("dir/c.txt")
      deleted("dir/subdir/d.txt")
      added("newfile.txt")
    }
  }

  fun testRenamedInWorktree() {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    // do not trigger move via VcsVFSListener
    rm("rename.txt")
    touch("unstaged.txt", "rename_file_content")
    repo.git("add -N unstaged.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    assertProviderChangesIn(listOf("rename.txt", "staged.txt", "unstaged.txt"),
                            listOf(null, null, MODIFIED))

    assertChanges {
      rename("rename.txt", "unstaged.txt")
    }
  }

  fun testTwiceRenamed() {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt staged.txt")

    // do not trigger move via VcsVFSListener
    rm("staged.txt")
    touch("unstaged.txt", "rename_file_content")
    repo.git("add -N unstaged.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    assertProviderChangesIn(listOf("rename.txt", "staged.txt", "unstaged.txt"),
                            listOf(null, MODIFIED, MODIFIED))

    assertChanges {
      rename("rename.txt", "staged.txt")
      rename("staged.txt", "unstaged.txt")
    }
  }

  fun testCaseOnlyRenamed() {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt RENAME.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                            listOf(null, MODIFIED))

    assertChanges {
      rename("rename.txt", "RENAME.txt")
    }
  }

  fun testRevertedTwiceRenamed() {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt staged.txt")

    // do not trigger move via VcsVFSListener
    rm("staged.txt")
    touch("rename.txt", "rename_file_content")
    repo.git("add -N rename.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    assertProviderChangesIn(listOf("rename.txt", "staged.txt"),
                            listOf(MODIFIED, MODIFIED))

    assertChanges {
      rename("rename.txt", "staged.txt")
      rename("staged.txt", "rename.txt")
    }
  }

  fun testCaseOnlyRevertedTwiceRenamed() {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt RENAME.txt")

    // do not trigger move via VcsVFSListener
    rm("RENAME.txt")
    assertFalse(child("RENAME.txt").exists())
    assertFalse(child("rename.txt").exists())

    touch("rename.txt", "rename_file_content")
    repo.git("add -N rename.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    if (SystemInfo.isWindows) {
      assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                              listOf(null, MODIFIED))

      assertChanges {
        rename("rename.txt", "RENAME.txt")
      }
    }
    else {
      assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                              listOf(MODIFIED, MODIFIED))

      assertChanges {
        rename("rename.txt", "RENAME.txt")
        rename("RENAME.txt", "rename.txt")
      }
    }
  }

  protected fun assertProviderChangesIn(files: List<String>, fileStatuses: List<FileStatus?>) {
    assertProviderChangesInPaths(files.map { VcsUtil.getFilePath(projectRoot, it) }, fileStatuses)
  }
}
