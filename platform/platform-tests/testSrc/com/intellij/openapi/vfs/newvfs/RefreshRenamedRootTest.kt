// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT as EdtThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `PersistentFSImpl.renameFile` renames a file in its file system before it renames the `VirtualFile`, and the listeners of the event
 * run in between. A refresh of the file that looks it up then, by the path that the VFS still has, must not delete it (IJPL-262431).
 */
@TestApplication
class RefreshRenamedRootTest {
  private val fs = RenameWindowTempFileSystem()

  @Test
  fun `a refresh that looks a file up while it is renamed keeps the file`(): Unit = timeoutRunBlocking {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fs))
    val (dir, file) = writeAction {
      val dir = root.createChildDirectory(this, "dir")
      dir to dir.createChildData(this, "a.txt")
    }
    val looked = CountDownLatch(1)
    val refreshed = CountDownLatch(1)
    fs.lookedUp = { if (it == file && !EdtThread.isCurrentThreadEdt()) looked.countDown() }
    fs.renamedOnDisk = {
      fs.renamedOnDisk = {}
      (file as NewVirtualFile).markDirty()
      RefreshQueue.getInstance().refresh(true, false, { refreshed.countDown() }, file)
      check(looked.await(10, TimeUnit.SECONDS)) { "the refresh must look the file up while it is renamed" }
    }

    withContext(Dispatchers.EDT) { writeAction { file.rename(this, "b.txt") } }

    assertThat(refreshed.await(10, TimeUnit.SECONDS)).describedAs("the refresh must finish").isTrue()
    assertThat(file.isValid).describedAs("the file exists under its new name").isTrue()
    assertThat(dir.findChild("b.txt")).isEqualTo(file)
  }
}

/** [renamedOnDisk] runs when the file system has renamed a file, before the VFS has; [lookedUp] runs when a file is looked up */
private class RenameWindowTempFileSystem : TempFileSystem() {
  private val protocol = "refresh-rename-${System.identityHashCode(this)}"

  @Volatile
  var renamedOnDisk: (VirtualFile) -> Unit = {}

  @Volatile
  var lookedUp: (VirtualFile) -> Unit = {}

  override fun getProtocol(): String = protocol

  override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
    super.renameFile(requestor, file, newName)
    renamedOnDisk(file)
  }

  override fun getAttributes(file: VirtualFile): FileAttributes? {
    lookedUp(file)
    return super.getAttributes(file)
  }
}
