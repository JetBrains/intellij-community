// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatus.ADDED
import com.intellij.openapi.vcs.FileStatus.DELETED
import com.intellij.openapi.vcs.FileStatus.MODIFIED
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.assertChangesWithRefresh
import git4idea.test.createDir
import git4idea.test.git
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitChangeProviderVersionedTest : GitChangeProviderTest() {
  @Test
  fun `test create file`(): Unit = with(context) {
    val file = create(projectRoot, "new.txt")
    repo.add(file.path)
    assertProviderChanges(file, ADDED)

    assertChangesWithRefresh {
      added("new.txt")
    }
  }

  @Test
  fun `test create file in dir`(): Unit = with(context) {
    val dir = runInEdtAndGet { createDir(projectRoot, "newdir") }
    dirty(dir)
    val bfile = create(dir, "new.txt")
    repo.add(bfile.path)
    assertProviderChanges(listOf(bfile, dir),
                          listOf(ADDED, null))

    assertChangesWithRefresh {
      added("newdir/new.txt")
    }
  }

  @Test
  fun `test edit file`(): Unit = with(context) {
    edit(atxt, "new content")
    assertProviderChanges(atxt, MODIFIED)

    assertChangesWithRefresh {
      modified("a.txt")
    }
  }

  @Test
  fun `test staged modification`(): Unit = with(context) {
    edit(atxt, "new content")
    repo.add(atxt.path)
    assertProviderChanges(atxt, MODIFIED)

    assertChangesWithRefresh {
      modified("a.txt")
    }
  }

  @Test
  fun `test staged unstaged modification`(): Unit = with(context) {
    edit(atxt, "new content")
    repo.add(atxt.path)
    edit(atxt, "new contents and some extra")
    assertProviderChanges(atxt, MODIFIED)

    assertChangesWithRefresh {
      modified("a.txt")
    }
  }

  @Test
  fun `test reverted staged modification`(): Unit = with(context) {
    val oldContent = VfsUtil.loadText(atxt)
    edit(atxt, "new content")
    repo.add(atxt.path)
    edit(atxt, oldContent)
    assertProviderChanges(atxt, null)

    assertChangesWithRefresh {
    }
  }

  @Test
  fun `test reverted staged addition`(): Unit = with(context) {
    val file = create(projectRoot, "new.txt")
    repo.add(file.path)
    cd(projectRoot)
    rm("new.txt")
    assertProviderChanges(atxt, null)

    assertChangesWithRefresh {
    }
  }

  @Test
  fun `test delete file`(): Unit = with(context) {
    deleteFile(atxt)
    assertProviderChanges(atxt, DELETED)

    assertChangesWithRefresh {
      deleted("a.txt")
    }
  }

  @Test
  fun `test delete dir recursively`(): Unit = with(context) {
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        val dir = projectRoot.findChild("dir")!!
        dirtyScope.addDirtyDirRecursively(VcsUtil.getFilePath(dir))
        FileUtil.delete(VfsUtilCore.virtualToIoFile(dir))
      }
    }
    assertProviderChanges(listOf(dir_ctxt, subdir_dtxt),
                          listOf(DELETED, DELETED))

    assertChangesWithRefresh {
      deleted("dir/c.txt")
      deleted("dir/subdir/d.txt")
    }
  }

  @Test
  fun `test simultaneous operations on multiple files`(): Unit = with(context) {
    edit(atxt, "new afile content")
    edit(dir_ctxt, "new cfile content")
    deleteFile(subdir_dtxt)
    val newfile = create(projectRoot, "newfile.txt")
    repo.add()

    assertProviderChanges(listOf(atxt, dir_ctxt, subdir_dtxt, newfile),
                          listOf(MODIFIED, MODIFIED, DELETED, ADDED))

    assertChangesWithRefresh {
      modified("a.txt")
      modified("dir/c.txt")
      deleted("dir/subdir/d.txt")
      added("newfile.txt")
    }
  }

  @Test
  fun `test renamed in worktree`(): Unit = with(context) {
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

    assertChangesWithRefresh {
      rename("rename.txt", "unstaged.txt")
    }
  }

  @Test
  fun `test twice renamed`(): Unit = with(context) {
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

    assertChangesWithRefresh {
      rename("rename.txt", "staged.txt")
      rename("staged.txt", "unstaged.txt")
    }
  }

  @Test
  fun `test case only renamed`(): Unit = with(context) {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt RENAME.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                            listOf(null, MODIFIED))

    assertChangesWithRefresh {
      rename("rename.txt", "RENAME.txt")
    }
  }

  @Test
  fun `test reverted twice renamed`(): Unit = with(context) {
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

    assertChangesWithRefresh {
      rename("rename.txt", "staged.txt")
      rename("staged.txt", "rename.txt")
    }
  }

  @Test
  fun `test case only reverted twice renamed`(): Unit = with(context) {
    assumeWorktreeRenamesSupported()

    touch("rename.txt", "rename_file_content")
    addCommit("init rename")

    repo.git("mv rename.txt RENAME.txt")

    // do not trigger move via VcsVFSListener
    rm("RENAME.txt")
    assertThat(child("RENAME.txt")).doesNotExist()
    assertThat(child("rename.txt")).doesNotExist()

    touch("rename.txt", "rename_file_content")
    repo.git("add -N rename.txt")

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    dirty(projectRoot)

    if (!SystemInfo.isFileSystemCaseSensitive) {
      assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                              listOf(null, MODIFIED))

      assertChangesWithRefresh {
        rename("rename.txt", "RENAME.txt")
      }
    }
    else {
      assertProviderChangesIn(listOf("rename.txt", "RENAME.txt"),
                              listOf(MODIFIED, MODIFIED))

      assertChangesWithRefresh {
        rename("rename.txt", "RENAME.txt")
        rename("RENAME.txt", "rename.txt")
      }
    }
  }

  private fun GitSingleRepoContext.assertProviderChangesIn(files: List<String>, fileStatuses: List<FileStatus?>) {
    assertProviderChangesInPaths(files.map { VcsUtil.getFilePath(projectRoot, it) }, fileStatuses)
  }
}
