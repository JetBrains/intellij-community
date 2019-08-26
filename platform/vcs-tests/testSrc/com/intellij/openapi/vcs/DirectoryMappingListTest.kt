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

  private lateinit var myMappings: NewMappings
  private lateinit var myProjectRoot: VirtualFile
  private lateinit var myRootPath: String
  private lateinit var myVcsManager: ProjectLevelVcsManagerImpl

  override fun setUpProject() {
    val root = FileUtil.toSystemIndependentName(VcsTestUtil.getTestDataPath() + BASE_PATH)

    myProjectRoot = PsiTestUtil.createTestProjectStructure(getTestName(true), null, root, myFilesToDelete, false)
    myRootPath = myProjectRoot.path

    myProject = ProjectManagerEx.getInstanceEx().loadProject("$myRootPath/directoryMappings.ipr")
    ProjectManagerEx.getInstanceEx().openTestProject(myProject)
    UIUtil.dispatchAllInvocationEvents() // startup activities

    val startupManager = StartupManager.getInstance(myProject) as StartupManagerImpl
    startupManager.runStartupActivities()
    val vcses = AllVcses.getInstance(myProject)
    vcses.registerManually(MockAbstractVcs(myProject, "mock"))
    vcses.registerManually(MockAbstractVcs(myProject, "CVS"))
    vcses.registerManually(MockAbstractVcs(myProject, "mock2"))

    myVcsManager = ProjectLevelVcsManager.getInstance(myProject) as ProjectLevelVcsManagerImpl
    myMappings = NewMappings(myProject, myVcsManager, DefaultVcsRootPolicy.getInstance(myProject))
    Disposer.register(testRootDisposable, myMappings)
    startupManager.runPostStartupActivities()
    myVcsManager.waitForInitialized()
  }

  fun testMappingsFilter() {
    (myVcsManager.findVcsByName("mock") as MockAbstractVcs).setAllowNestedRoots(true)

    val pathsStr = listOf("$myRootPath/a",
                          "$myRootPath/a/b",
                          "$myRootPath/def",
                          "$myRootPath/a-b",
                          "$myRootPath/a-b/d-e",
                          "$myRootPath/a-b1/d-e")
    val a = myProjectRoot.findChild("a")!!
    createChildDirectory(a, "b")
    createChildDirectory(myProjectRoot, "def")
    val ab = myProjectRoot.findChild("a-b")!!
    val ab1 = createChildDirectory(myProjectRoot, "a-b1")
    createChildDirectory(ab, "d-e")
    createChildDirectory(ab1, "d-e")

    myVcsManager.directoryMappings = listOf(
      VcsDirectoryMapping(pathsStr[0], "mock"),
      VcsDirectoryMapping(pathsStr[1], "mock"),
      VcsDirectoryMapping(pathsStr[2], "mock"),
      VcsDirectoryMapping(pathsStr[3], "mock2"),
      VcsDirectoryMapping(pathsStr[4], "mock2"),
      VcsDirectoryMapping(pathsStr[5], "mock2"))

    val paths = mutableListOf<FilePath>()
    for (path in pathsStr) {
      paths.add(VcsUtil.getFilePath(path, true))
    }

    assertEquals(6, myVcsManager.directoryMappings.size)
    val filePaths = DescindingFilesFilter.filterDescindingFiles(paths.toTypedArray(), myProject)
    assertEquals(5, filePaths.size)
  }

  fun testSamePrefix() {
    val childA = myProjectRoot.findChild("a")!!
    val childAB = myProjectRoot.findChild("a-b")!!

    myMappings.setMapping("$myRootPath/a", "CVS")
    myMappings.setMapping("$myRootPath/a-b", "mock2")
    assertEquals(2, myMappings.directoryMappings.size)
    myMappings.cleanupMappings()
    assertEquals(2, myMappings.directoryMappings.size)
    assertEquals("mock2", getVcsFor(childAB))
    assertEquals("CVS", getVcsFor(childA))
  }

  fun testSamePrefixEmpty() {
    val childAB = myProjectRoot.findChild("a-b")!!

    myMappings.setMapping("$myRootPath/a", "CVS")
    assertNull(getVcsFor(childAB))
  }

  fun testSame() {
    myMappings.setMapping("$myRootPath/parent/path1", "CVS")
    myMappings.setMapping("$myRootPath\\parent\\path2", "CVS")

    val children = listOf(
      "$myRootPath\\parent\\path1",
      "$myRootPath/parent/path1",
      "$myRootPath\\parent\\path1",
      "$myRootPath\\parent\\path2",
      "$myRootPath/parent/path2",
      "$myRootPath\\parent\\path2"
    )
    createFiles(children)

    for (child in children) {
      myMappings.setMapping(child, "CVS")
      myMappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 2, myMappings.directoryMappings.size)
    }

    for (child in children) {
      myMappings.setMapping(child, "CVS")
      assertEquals("cleanup failed: $child", 2, myMappings.directoryMappings.size)
    }
  }

  fun testHierarchy() {
    myMappings.setMapping("$myRootPath/parent", "CVS")

    val children = listOf(
      "$myRootPath/parent/child1",
      "$myRootPath/parent/middle/child2",
      "$myRootPath/parent/middle/child3"
    )
    createFiles(children)

    for (child in children) {
      myMappings.setMapping(child, "CVS")
      myMappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 1, myMappings.directoryMappings.size)
    }
  }

  fun testNestedInnerCopy() {
    myMappings.setMapping("$myRootPath/parent", "CVS")
    myMappings.setMapping("$myRootPath/parent/child", "mock")

    val children = listOf(
      "$myRootPath/parent/child1",
      "$myRootPath\\parent\\middle\\child2",
      "$myRootPath/parent/middle/child3",
      "$myRootPath/parent/child/inner"
    )
    createFiles(children)

    myMappings.waitMappedRootsUpdate()

    val awaitedVcsNames = listOf("CVS", "CVS", "CVS", "mock")
    val lfs = LocalFileSystem.getInstance()
    for (i in children.indices) {
      val child = children[i]
      val vf = lfs.refreshAndFindFileByIoFile(File(child))!!
      assertNotNull("No file for: $child", vf)
      val mapping = getMappingFor(vf)
      assertNotNull("No mapping for: $vf", mapping)
      assertEquals(awaitedVcsNames[i], mapping!!.vcs)
    }
  }

  private fun createFiles(paths: List<String>) {
    for (path in paths) {
      val file = File(FileUtil.toSystemDependentName(path))
      val created = file.mkdirs()
      assertTrue("Can't create file: $file", created || file.isDirectory)
      myFilesToDelete.add(file)
    }
    LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete)
  }

  private fun getVcsFor(file: VirtualFile): String? {
    val root = myMappings.getMappedRootFor(file)
    return root?.vcs?.name
  }

  private fun getMappingFor(file: VirtualFile): VcsDirectoryMapping? {
    val root = myMappings.getMappedRootFor(file)
    return root?.mapping
  }
}