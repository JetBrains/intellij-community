// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.application
import org.junit.Assume

private const val LOCAL_FILESYSTEM_CHANGE_ATTEMPTS = 3

class CanonicallyCasedVirtualFileUrlManagerTest : HeavyPlatformTestCase() {
  fun testCanonicallyCasedVirtualFileUrlManager() {
    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelInternal

    val virtualFileUrlManager = workspaceModel.getCanonicallyCasedVirtualFileUrlManager()

    val virtualFileUrl = createTestJarUrl(virtualFileUrlManager)
    assertGetCanonicallyCasedNameCalled(virtualFileUrl, called = false)
  }

  fun testDefaultVirtualFileUrlManager() {
    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelInternal

    val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()

    val virtualFileUrl = createTestJarUrl(virtualFileUrlManager)
    assertGetCanonicallyCasedNameCalled(virtualFileUrl, called = true)
  }

  private fun createTestJarUrl(virtualFileUrlManager: VirtualFileUrlManager): VirtualFileUrl {
    val jarFile = tempDir.newPath("test.jar").createFile()
    val url = "jar://$jarFile!/"
    return virtualFileUrlManager.getOrCreateFromUrl(url)
  }

  private fun assertGetCanonicallyCasedNameCalled(virtualFileUrl: VirtualFileUrl, called: Boolean) {
    val mockLocalFileSystem = LocalFileSystem.getInstance() as MockLocalFileSystem
    mockLocalFileSystem.getCanonicallyCasedNameWasCalled = false

    val virtualFile = virtualFileUrl.virtualFile

    checkNotNull(virtualFile)
    assertEquals(called, mockLocalFileSystem.getCanonicallyCasedNameWasCalled)
  }

  override fun setUp() {
    super.setUp()
    Assume.assumeFalse("Test has to be run on Windows or macOS", SystemInfoRt.isFileSystemCaseSensitive)

    application.registerOrReplaceServiceInstance(VirtualFileManager::class.java, MockVirtualFileManager(), testRootDisposable)
    ensureLocalFileSystem(isMocked = true)
  }

  override fun tearDown() {
    try {
      disposeRootDisposable()
      ensureLocalFileSystem(isMocked = false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun ensureLocalFileSystem(isMocked: Boolean) {
    fun isCorrectLocalFileSystem(): Boolean =
      LocalFileSystem.getInstance() is MockLocalFileSystem == isMocked

    for (attempt in 1..LOCAL_FILESYSTEM_CHANGE_ATTEMPTS) {
      ApplicationManager.runCleaners()
      if (attempt > 1) {
        Thread.sleep(1000)
      }
      if (isCorrectLocalFileSystem()) break
    }
    assertTrue("Could not change the filesystem to isMocked=$isMocked", isCorrectLocalFileSystem())

    // Ensure that a LocalFileSystem instance is not cached inside VirtualDirectoryImpl
    val fs = PersistentFS.getInstance() as PersistentFSImpl
    fs.disconnect()
    fs.connect()
  }
}

private class MockVirtualFileManager() : PlatformVirtualFileManager() {
  private val mockLocalFileSystem: MockLocalFileSystem by lazy {
    MockLocalFileSystem()
  }

  override fun getFileSystemsForProtocol(protocol: String): List<VirtualFileSystem> {
    if (protocol == LocalFileSystem.PROTOCOL) {
      return listOf(mockLocalFileSystem)
    }
    return super.getFileSystemsForProtocol(protocol)
  }
}

private class MockLocalFileSystem : LocalFileSystemImpl() {
  var getCanonicallyCasedNameWasCalled = false

  override fun toString(): String = "MockLocalFileSystem"

  override fun getCanonicallyCasedName(file: VirtualFile): String {
    getCanonicallyCasedNameWasCalled = true
    @Suppress("removal")
    return super.getCanonicallyCasedName(file)
  }
}
