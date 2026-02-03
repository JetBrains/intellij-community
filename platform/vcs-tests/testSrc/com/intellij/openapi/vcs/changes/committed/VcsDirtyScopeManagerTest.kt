// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcsUtil.VcsUtil.getFilePath

/**
 * @see VcsDirtyScopeTest
 */
class VcsDirtyScopeManagerTest : VcsPlatformTest() {

  private lateinit var dirtyScopeManager: VcsDirtyScopeManagerImpl
  private lateinit var vcs: MockAbstractVcs
  private lateinit var basePath: FilePath

  override fun setUp() {
    super.setUp()
    dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project)
    vcs = MockAbstractVcs(project)
    basePath = getFilePath(projectRoot)

    disableVcsDirtyScopeVfsListener()
    disableChangeListManager()

    vcsManager.registerVcs(vcs)
    vcsManager.waitForInitialized()
    registerRootMapping(projectRoot)
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#com.intellij.openapi.vcs.changes")

  fun `test simple case`() {
    val file = createFile(projectRoot, "file.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = retrieveDirtyScopes()
    assertTrue(invalidated.isFileDirty(file))
    val scope = assertOneScope(invalidated)
    assertTrue(scope.dirtyFiles.contains(file))
  }

  fun `test recursively dirty directory makes files under it dirty`() {
    val dir = createDir(projectRoot, "dir")
    val file = createFile(dir, "file.txt")

    dirtyScopeManager.dirDirtyRecursively(dir)

    val invalidated = retrieveDirtyScopes()
    assertTrue(invalidated.isFileDirty(file))
    val scope = assertOneScope(invalidated)
    assertTrue(scope.recursivelyDirtyDirectories.contains(dir))
    assertFalse(scope.dirtyFiles.contains(file))
  }

  fun `test dirty files from different roots`() {
    val otherRoot = createSubRoot(testRoot, "otherRoot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(file), listOf(otherRoot))

    val invalidated = retrieveDirtyScopes()
    assertDirtiness(invalidated, file, subFile)
    val scope = assertOneScope(invalidated)
    assertTrue(scope.recursivelyDirtyDirectories.contains(otherRoot))
    assertTrue(scope.dirtyFiles.contains(file))
  }

  fun `test mark everything dirty should mark dirty all roots`() {
    val subRoot = createSubRoot(projectRoot, "subroot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(subRoot, "sub.txt")

    dirtyScopeManager.markEverythingDirty()

    val invalidated = retrieveDirtyScopes()
    assertDirtiness(invalidated, file, basePath, subRoot, subFile)
  }

  // this is already implicitly checked in several other tests, but better to have it explicit
  fun `test all roots from a single vcs belong to a single scope`() {
    val otherRoot = createSubRoot(testRoot, "otherRoot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(), listOf(basePath, otherRoot))

    val invalidated = retrieveDirtyScopes()
    val scope = assertOneScope(invalidated)
    assertDirtiness(invalidated, file, subFile)
    assertTrue(scope.recursivelyDirtyDirectories.contains(basePath))
    assertTrue(scope.recursivelyDirtyDirectories.contains(otherRoot))
  }

  fun `test marking file outside of any VCS root dirty has no effect`() {
    val file = createFile(testRoot, "outside.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = retrieveDirtyScopes()
    assertTrue(invalidated.isEmpty())
    assertFalse(invalidated.isEverythingDirty)
  }

  fun `test mark files from different VCSs dirty produce two dirty scopes`() {
    val basePath = getFilePath(projectRoot)
    val subRoot = createDir(projectRoot, "othervcs")
    val otherVcs = MockAbstractVcs(project, "otherVCS")
    vcsManager.registerVcs(otherVcs)
    registerRootMapping(subRoot.virtualFile!!, otherVcs)

    dirtyScopeManager.filePathsDirty(null, listOf(basePath, subRoot))

    val invalidated = retrieveDirtyScopes()
    val scopes = invalidated.scopes
    assertEquals(2, scopes.size)
    val mainVcsScope = scopes.find { it.vcs.name == vcs.name }!!
    assertTrue(mainVcsScope.recursivelyDirtyDirectories.contains(basePath))
    val otherVcsScope = scopes.find { it.vcs.name == otherVcs.name }!!
    assertTrue(otherVcsScope.recursivelyDirtyDirectories.contains(subRoot))
  }

  private fun retrieveDirtyScopes(): VcsInvalidated {
    val scopes = dirtyScopeManager.retrieveScopes()!!
    dirtyScopeManager.changesProcessed()
    return scopes
  }

  private fun disableVcsDirtyScopeVfsListener() {
    project.service<VcsDirtyScopeVfsListener>().setForbid(true)
  }

  private fun disableChangeListManager() {
    (ChangeListManager.getInstance(project) as ChangeListManagerImpl).forceStopInTestMode()
  }

  private fun createSubRoot(parent: VirtualFile, name: String): FilePath {
    val dir = createDir(parent, name)
    registerRootMapping(dir.virtualFile!!)
    return dir
  }

  private fun registerRootMapping(root: VirtualFile) {
    registerRootMapping(root, vcs)
  }

  private fun registerRootMapping(root: VirtualFile, vcs: AbstractVcs) {
    vcsManager.setDirectoryMapping(root.path, vcs.name)
    dirtyScopeManager.retrieveScopes() // ignore the dirty event after adding the mapping
    dirtyScopeManager.changesProcessed()
  }

  private fun createFile(parentDir: FilePath, name: String): FilePath {
    return createFile(parentDir.virtualFile!!, name, false)
  }

  private fun createFile(parentDir: VirtualFile, name: String): FilePath {
    return createFile(parentDir, name, false)
  }

  private fun createDir(parentDir: VirtualFile, name: String): FilePath {
    return createFile(parentDir, name, true)
  }

  private fun createFile(parentDir: VirtualFile, name: String, dir: Boolean): FilePath {
    var file: VirtualFile? = null
    runInEdtAndWait {
      ApplicationManager.getApplication().runWriteAction {
        file = if (dir) parentDir.createChildDirectory(this, name) else parentDir.createChildData(this, name)
      }
    }
    return getFilePath(file!!)
  }

  private fun assertOneScope(invalidated: VcsInvalidated): VcsDirtyScope {
    assertEquals(1, invalidated.scopes.size)
    return invalidated.scopes.first()
  }

  private fun assertDirtiness(invalidated: VcsInvalidated, vararg dirty: FilePath) {
    dirty.forEach {
      assertTrue("File $it is not dirty", invalidated.isFileDirty(it))
    }
  }
}
