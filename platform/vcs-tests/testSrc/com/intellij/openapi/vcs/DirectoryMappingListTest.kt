// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.actions.DescindingFilesFilter
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import java.io.File

class DirectoryMappingListTest : HeavyPlatformTestCase() {
  private val BASE_PATH = "/vcs/directoryMappings/"

  private lateinit var mappings: NewMappings
  private lateinit var projectRoot: VirtualFile
  private lateinit var rootPath: String

  private lateinit var vcsManager: ProjectLevelVcsManagerImpl

  private lateinit var vcsMock: MockAbstractVcs
  private lateinit var vcsMock2: MockAbstractVcs
  private lateinit var vcsCVS: MockAbstractVcs

  override fun setUpProject() {
    val root = FileUtil.toSystemIndependentName(VcsTestUtil.getTestDataPath() + BASE_PATH)

    projectRoot = PsiTestUtil.createTestProjectStructure(getTestName(true), null, root, myFilesToDelete, false)
    rootPath = projectRoot.path

    myProject = ProjectManagerEx.getInstanceEx().loadProject("$rootPath/directoryMappings.ipr")
    ProjectManagerEx.getInstanceEx().openTestProject(myProject)
    UIUtil.dispatchAllInvocationEvents() // startup activities

    val startupManager = StartupManager.getInstance(myProject) as StartupManagerImpl
    startupManager.runStartupActivities()

    vcsMock = MockAbstractVcs(myProject, "mock")
    vcsMock2 = MockAbstractVcs(myProject, "mock2")
    vcsCVS = MockAbstractVcs(myProject, "CVS")

    val vcses = AllVcses.getInstance(myProject)
    vcses.registerManually(vcsMock)
    vcses.registerManually(vcsMock2)
    vcses.registerManually(vcsCVS)

    vcsManager = ProjectLevelVcsManager.getInstance(myProject) as ProjectLevelVcsManagerImpl
    mappings = NewMappings(myProject, vcsManager, DefaultVcsRootPolicy.getInstance(myProject))
    Disposer.register(testRootDisposable, mappings)
    startupManager.runPostStartupActivities()
    vcsManager.waitForInitialized()
  }

  fun testMappingsFilter() {
    vcsMock.setAllowNestedRoots(true)

    val pathsStr = listOf("$rootPath/a",
                          "$rootPath/a/b",
                          "$rootPath/def",
                          "$rootPath/a-b",
                          "$rootPath/a-b/d-e",
                          "$rootPath/a-b1/d-e")
    createDirectories(pathsStr)

    vcsManager.directoryMappings = listOf(
      VcsDirectoryMapping(pathsStr[0], "mock"),
      VcsDirectoryMapping(pathsStr[1], "mock"),
      VcsDirectoryMapping(pathsStr[2], "mock"),
      VcsDirectoryMapping(pathsStr[3], "mock2"),
      VcsDirectoryMapping(pathsStr[4], "mock2"),
      VcsDirectoryMapping(pathsStr[5], "mock2"))

    assertEquals(6, vcsManager.directoryMappings.size)
    assertEquals(3, vcsManager.getRootsUnderVcs(vcsMock).size)
    assertEquals(2, vcsManager.getRootsUnderVcs(vcsMock2).size) // No nested roots allowed

    val paths = pathsStr.map { VcsUtil.getFilePath(it, true) }
    val filePaths = DescindingFilesFilter.filterDescindingFiles(paths.toTypedArray(), myProject)
    assertEquals(5, filePaths.size)
  }

  fun testSamePrefix() {
    val childA = projectRoot.findChild("a")!!
    val childAB = projectRoot.findChild("a-b")!!

    mappings.setMapping("$rootPath/a", "CVS")
    mappings.setMapping("$rootPath/a-b", "mock2")
    assertEquals(2, mappings.directoryMappings.size)

    mappings.cleanupMappings()
    assertEquals(2, mappings.directoryMappings.size)
    assertEquals("mock2", getVcsFor(childAB))
    assertEquals("CVS", getVcsFor(childA))
  }

  fun testSamePrefixEmpty() {
    val childAB = projectRoot.findChild("a-b")!!

    mappings.setMapping("$rootPath/a", "CVS")
    assertNull(getVcsFor(childAB))
  }

  fun testSame() {
    mappings.setMapping("$rootPath/parent/path1", "CVS")
    mappings.setMapping("$rootPath\\parent\\path2", "CVS")

    val children = listOf(
      "$rootPath\\parent\\path1",
      "$rootPath/parent/path1",
      "$rootPath\\parent\\path1",
      "$rootPath\\parent\\path2",
      "$rootPath/parent/path2",
      "$rootPath\\parent\\path2"
    )
    createDirectories(children)

    for (child in children) {
      mappings.setMapping(child, "CVS")
      mappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 2, mappings.directoryMappings.size)
    }

    for (child in children) {
      mappings.setMapping(child, "CVS")
      assertEquals("cleanup failed: $child", 2, mappings.directoryMappings.size)
    }
  }

  fun testHierarchy() {
    mappings.setMapping("$rootPath/parent", "CVS")

    val children = listOf(
      "$rootPath/parent/child1",
      "$rootPath/parent/middle/child2",
      "$rootPath/parent/middle/child3"
    )
    createDirectories(children)

    for (child in children) {
      mappings.setMapping(child, "CVS")
      mappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 1, mappings.directoryMappings.size)
    }
  }

  fun testNestedInnerCopy() {
    mappings.setMapping("$rootPath/parent", "CVS")
    mappings.setMapping("$rootPath/parent/child", "mock")

    val children = listOf(
      "$rootPath/parent/child1",
      "$rootPath\\parent\\middle\\child2",
      "$rootPath/parent/middle/child3",
      "$rootPath/parent/child/inner"
    )
    val files = createDirectories(children)

    mappings.waitMappedRootsUpdate()

    val awaitedVcsNames = listOf("CVS", "CVS", "CVS", "mock")
    for (i in children.indices) {
      val mapping = getMappingFor(files[i])
      assertEquals(awaitedVcsNames[i], mapping?.vcs)
    }
  }

  private fun createDirectories(paths: List<String>): List<VirtualFile> {
    return paths.map { createDirectory(it) }
  }

  private fun createDirectory(path: String): VirtualFile {
    val file = File(FileUtil.toSystemDependentName(path))
    val created = file.exists() && file.isDirectory || file.mkdirs()
    assertTrue("Can't create file: $file", created)
    myFilesToDelete.add(file)
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
  }

  private fun getVcsFor(file: VirtualFile): String? {
    val root = mappings.getMappedRootFor(file)
    return root?.vcs?.name
  }

  private fun getMappingFor(file: VirtualFile): VcsDirectoryMapping? {
    val root = mappings.getMappedRootFor(file)
    return root?.mapping
  }
}