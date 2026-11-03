// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeVfsListener
import com.intellij.openapi.vcs.changes.VcsInvalidated
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.test.VcsPlatformTestContext
import com.intellij.vcs.test.testRoot
import com.intellij.vcs.test.vcsPlatformContextFixture
import com.intellij.vcsUtil.VcsUtil.getFilePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @see VcsDirtyScopeTest
 */
@TestApplication
class VcsDirtyScopeManagerTest {
  private val fixture = vcsPlatformContextFixture()
  private val context: VcsPlatformTestContext get() = fixture.get()

  private lateinit var dirtyScopeManager: VcsDirtyScopeManagerImpl
  private lateinit var vcs: MockAbstractVcs
  private lateinit var basePath: FilePath

  @BeforeEach
  fun setUp(): Unit = with(context) {
    dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project)
    vcs = MockAbstractVcs(project)
    basePath = getFilePath(projectRoot)

    disableVcsDirtyScopeVfsListener()
    disableChangeListManager()

    vcsManager.registerVcs(vcs)
    vcsManager.waitForInitialized()
    registerRootMapping(projectRoot)
  }

  @Test
  fun `test simple case`(): Unit = with(context) {
    val file = createFile(projectRoot, "file.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = retrieveDirtyScopes()
    assertThat(invalidated.isFileDirty(file)).isTrue()
    val scope = assertOneScope(invalidated)
    assertThat(scope.dirtyFiles).contains(file)
  }

  @Test
  fun `test recursively dirty directory makes files under it dirty`(): Unit = with(context) {
    val dir = createDir(projectRoot, "dir")
    val file = createFile(dir, "file.txt")

    dirtyScopeManager.dirDirtyRecursively(dir)

    val invalidated = retrieveDirtyScopes()
    assertThat(invalidated.isFileDirty(file)).isTrue()
    val scope = assertOneScope(invalidated)
    assertThat(scope.recursivelyDirtyDirectories).contains(dir)
    assertThat(scope.dirtyFiles).doesNotContain(file)
  }

  @Test
  fun `test dirty files from different roots`(): Unit = with(context) {
    val otherRoot = createSubRoot(testRoot, "otherRoot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(file), listOf(otherRoot))

    val invalidated = retrieveDirtyScopes()
    assertDirtiness(invalidated, file, subFile)
    val scope = assertOneScope(invalidated)
    assertThat(scope.recursivelyDirtyDirectories).contains(otherRoot)
    assertThat(scope.dirtyFiles).contains(file)
  }

  @Test
  fun `test mark everything dirty should mark dirty all roots`(): Unit = with(context) {
    val subRoot = createSubRoot(projectRoot, "subroot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(subRoot, "sub.txt")

    dirtyScopeManager.markEverythingDirty()

    val invalidated = retrieveDirtyScopes()
    assertDirtiness(invalidated, file, basePath, subRoot, subFile)
  }

  // this is already implicitly checked in several other tests, but better to have it explicit
  @Test
  fun `test all roots from a single vcs belong to a single scope`(): Unit = with(context) {
    val otherRoot = createSubRoot(testRoot, "otherRoot")
    val file = createFile(projectRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(), listOf(basePath, otherRoot))

    val invalidated = retrieveDirtyScopes()
    val scope = assertOneScope(invalidated)
    assertDirtiness(invalidated, file, subFile)
    assertThat(scope.recursivelyDirtyDirectories).contains(basePath)
    assertThat(scope.recursivelyDirtyDirectories).contains(otherRoot)
  }

  @Test
  fun `test marking file outside of any VCS root dirty has no effect`(): Unit = with(context) {
    val file = createFile(testRoot, "outside.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = retrieveDirtyScopes()
    assertThat(invalidated.isEmpty()).isTrue()
    assertThat(invalidated.isEverythingDirty).isFalse()
  }

  @Test
  fun `test mark files from different VCSs dirty produce two dirty scopes`(): Unit = with(context) {
    val basePath = getFilePath(projectRoot)
    val subRoot = createDir(projectRoot, "othervcs")
    val otherVcs = MockAbstractVcs(project, "otherVCS")
    vcsManager.registerVcs(otherVcs)
    registerRootMapping(subRoot.virtualFile!!, otherVcs)

    dirtyScopeManager.filePathsDirty(null, listOf(basePath, subRoot))

    val invalidated = retrieveDirtyScopes()
    val scopes = invalidated.scopes
    assertThat(scopes).hasSize(2)
    val mainVcsScope = scopes.find { it.vcs.name == vcs.name }!!
    assertThat(mainVcsScope.recursivelyDirtyDirectories).contains(basePath)
    val otherVcsScope = scopes.find { it.vcs.name == otherVcs.name }!!
    assertThat(otherVcsScope.recursivelyDirtyDirectories).contains(subRoot)
  }

  private fun retrieveDirtyScopes(): VcsInvalidated {
    val scopes = dirtyScopeManager.retrieveScopes()!!
    dirtyScopeManager.changesProcessed()
    return scopes
  }

  private fun VcsPlatformTestContext.disableVcsDirtyScopeVfsListener() {
    project.service<VcsDirtyScopeVfsListener>().setForbid(true)
  }

  private fun VcsPlatformTestContext.disableChangeListManager() {
    (ChangeListManager.getInstance(project) as ChangeListManagerImpl).forceStopInTestMode()
  }

  private fun VcsPlatformTestContext.createSubRoot(parent: VirtualFile, name: String): FilePath {
    val dir = createDir(parent, name)
    registerRootMapping(dir.virtualFile!!)
    return dir
  }

  private fun VcsPlatformTestContext.registerRootMapping(root: VirtualFile) {
    registerRootMapping(root, vcs)
  }

  private fun VcsPlatformTestContext.registerRootMapping(root: VirtualFile, vcs: AbstractVcs) {
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
    assertThat(invalidated.scopes).hasSize(1)
    return invalidated.scopes.first()
  }

  private fun assertDirtiness(invalidated: VcsInvalidated, vararg dirty: FilePath) {
    dirty.forEach {
      assertThat(invalidated.isFileDirty(it)).describedAs("File $it is not dirty").isTrue()
    }
  }
}
