// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Reproducer: a lock-free `getFileInfo` reader races with the rebuild of non-incremental file sets.
 *
 * The reader blocks inside the exclude condition of a synthetic library, which runs in the first pass over the
 * file sets of the source root. While it waits, the rebuild removes these file sets in place. The second pass then
 * sees an empty list, and `getFileInfo` throws `NoSuchElementException: List is empty.`
 */
@TestApplication
class SyntheticLibraryLockFreeReadRaceTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val baseLibraryDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `lock-free getFileInfo survives a rebuild of non-incremental file sets`(): Unit = timeoutRunBlocking {
    this@SyntheticLibraryLockFreeReadRaceTest.blockReaderInExcludeCondition(rebuildWhileBlocked = true)
  }

  @Test
  fun `control - lock-free getFileInfo with a blocked reader and no rebuild`(): Unit = timeoutRunBlocking {
    this@SyntheticLibraryLockFreeReadRaceTest.blockReaderInExcludeCondition(rebuildWhileBlocked = false)
  }

  private suspend fun blockReaderInExcludeCondition(rebuildWhileBlocked: Boolean) {
    val srcRoot = this.baseLibraryDir.newVirtualDirectory("lib/src")
    val srcFile = this.baseLibraryDir.newVirtualFile("lib/src/a.go")
    val readerEntered = CountDownLatch(1)
    val releaseReader = CountDownLatch(1)
    val readerThread = AtomicReference<Thread>()
    val excludeCondition = Condition<VirtualFile> {
      if (Thread.currentThread() === readerThread.get() && readerEntered.count > 0) {
        readerEntered.countDown()
        releaseReader.await(30, TimeUnit.SECONDS)
      }
      false
    }
    val library = SyntheticLibrary.newImmutableLibrary(listOf(srcRoot), emptyList(), emptySet(), excludeCondition)
    val registration = Disposer.newDisposable()
    try {
      withContext(Dispatchers.EDT) {
        this@SyntheticLibraryLockFreeReadRaceTest.registerSyntheticLibrary(library, registration)
        // Let the invokeLater from ProjectRootManagerComponent finish.
        yield()
      }

      val fileIndex = WorkspaceFileIndexEx.getInstance(this.projectModel.project)
      assertTrue(fileIndex.getFileInfo(srcFile, true, true, true, true, true, true, true) !is WorkspaceFileInternalInfo.NonWorkspace)

      val readerFailure = AtomicReference<Throwable>()
      val reader = Thread {
        try {
          fileIndex.getFileInfo(srcFile, true, true, true, true, true, true, true)
        }
        catch (t: Throwable) {
          readerFailure.set(t)
        }
      }
      readerThread.set(reader)
      reader.start()
      assertTrue(readerEntered.await(30, TimeUnit.SECONDS), "the reader did not reach the exclude condition")

      if (rebuildWhileBlocked) {
        edtWriteAction {
          fileIndex.indexData.resetCustomContributors()
        }
        fileIndex.getFileInfo(srcRoot, true, true, true, true, true, true, true)
      }

      releaseReader.countDown()
      reader.join(30_000)
      readerFailure.get()?.let { throw it }
    }
    finally {
      releaseReader.countDown()
      withContext(Dispatchers.EDT) {
        Disposer.dispose(registration)
        yield()
      }
    }
  }

  private fun registerSyntheticLibrary(library: SyntheticLibrary, disposable: Disposable) {
    val provider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(library)
      }
    }
    AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(provider, disposable)
  }
}
