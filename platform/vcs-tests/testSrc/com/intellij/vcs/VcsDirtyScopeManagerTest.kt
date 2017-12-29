/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcsUtil.VcsUtil.getFilePath

// see also VcsDirtyScopeTest
class VcsDirtyScopeManagerTest : VcsPlatformTest() {

  private lateinit var dirtyScopeManager: VcsDirtyScopeManager
  private lateinit var vcs: MockAbstractVcs
  private lateinit var baseRoot: VirtualFile
  private lateinit var basePath: FilePath

  override fun setUp() {
    super.setUp()
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
    vcs = MockAbstractVcs(project)
    baseRoot = project.baseDir
    basePath = getFilePath(baseRoot)

    disableVcsDirtyScopeVfsListener()
    disableChangeListManager()

    vcsManager.registerVcs(vcs)
    registerRootMapping(baseRoot)
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#com.intellij.openapi.vcs.changes")

  fun `test simple case`() {
    val file = createFile(baseRoot, "file.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertTrue(invalidated.isFileDirty(file))
    val scope = assertOneScope(invalidated)
    assertTrue(scope.dirtyFiles.contains(file))
  }

  fun `test recursively dirty directory makes files under it dirty`() {
    val dir = createDir(baseRoot, "dir")
    val file = createFile(dir, "file.txt")

    dirtyScopeManager.dirDirtyRecursively(dir)

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertTrue(invalidated.isFileDirty(file))
    val scope = assertOneScope(invalidated)
    assertTrue(scope.recursivelyDirtyDirectories.contains(dir))
    assertFalse(scope.dirtyFiles.contains(file))
  }

  fun `test dirty files from different roots`() {
    val otherRoot = createSubRoot(testRootFile, "otherRoot")
    val file = createFile(baseRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(file), listOf(otherRoot))

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertDirtiness(invalidated, file, otherRoot, subFile)
    val scope = assertOneScope(invalidated)
    assertTrue(scope.recursivelyDirtyDirectories.contains(otherRoot))
    assertTrue(scope.dirtyFiles.contains(file))
  }

  fun `test mark everything dirty should mark dirty all roots`() {
    val subRoot = createSubRoot(baseRoot, "subroot")
    val file = createFile(baseRoot, "file.txt")
    val subFile = createFile(subRoot, "sub.txt")

    dirtyScopeManager.markEverythingDirty()

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertDirtiness(invalidated, file, basePath, subRoot, subFile)
  }

  // this is already implicitly checked in several other tests, but better to have it explicit
  fun `test all roots from a single vcs belong to a single scope`() {
    val otherRoot = createSubRoot(testRootFile, "otherRoot")
    val file = createFile(baseRoot, "file.txt")
    val subFile = createFile(otherRoot, "other.txt")

    dirtyScopeManager.filePathsDirty(listOf(), listOf(basePath, otherRoot))

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertOneScope(invalidated)
    assertDirtiness(invalidated, file, otherRoot, subFile)
  }

  fun `test marking file outside of any VCS root dirty has no effect`() {
    val file = createFile(testRootFile, "outside.txt")

    dirtyScopeManager.fileDirty(file)

    val invalidated = dirtyScopeManager.retrieveScopes()
    assertTrue(invalidated.isEmpty)
    assertFalse(invalidated.isEverythingDirty)
  }

  fun `test mark files from different VCSs dirty produce two dirty scopes`() {
    val basePath = getFilePath(baseRoot)
    val subRoot = createDir(baseRoot, "othervcs")
    val otherVcs = MockAbstractVcs(project, "otherVCS")
    vcsManager.registerVcs(otherVcs)
    registerRootMapping(subRoot.virtualFile!!, otherVcs)

    dirtyScopeManager.filePathsDirty(null, listOf(basePath, subRoot))

    val invalidated = dirtyScopeManager.retrieveScopes()
    val scopes = invalidated.scopes
    assertEquals(2, scopes.size)
    val mainVcsScope = scopes.find { it.vcs.name == vcs.name }!!
    assertTrue(mainVcsScope.recursivelyDirtyDirectories.contains(basePath))
    val otherVcsScope = scopes.find { it.vcs.name == otherVcs.name }!!
    assertTrue(otherVcsScope.recursivelyDirtyDirectories.contains(subRoot))
  }

  private fun disableVcsDirtyScopeVfsListener() {
    project.service<VcsDirtyScopeVfsListener>().setForbid(true)
  }

  private fun disableChangeListManager() {
    (ChangeListManager.getInstance(project) as ChangeListManagerImpl).freeze("For tests")
  }

  private fun createSubRoot(parent: VirtualFile, name: String): FilePath {
    val dir = createDir(parent, name)
    registerRootMapping(dir.virtualFile!!)
    return dir
  }

  private fun registerRootMapping(root: VirtualFile) {
    registerRootMapping(root, vcs)
  }

  private fun registerRootMapping(root: VirtualFile, vcs: AbstractVcs<*>) {
    vcsManager.setDirectoryMapping(root.path, vcs.name)
    dirtyScopeManager.retrieveScopes() // ignore the dirty event after adding the mapping
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
    val scope = invalidated.scopes.first()
    return scope
  }

  private fun assertDirtiness(invalidated: VcsInvalidated, vararg dirty: FilePath, clean: Collection<FilePath> = emptyList()) {
    dirty.forEach { assertTrue(invalidated.isFileDirty(it)) }
    clean.forEach { assertFalse(invalidated.isFileDirty(it)) }
  }
}
