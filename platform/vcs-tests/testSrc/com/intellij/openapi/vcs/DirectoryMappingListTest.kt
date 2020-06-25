// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.actions.DescindingFilesFilter
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitialization
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.vcsUtil.VcsUtil
import org.junit.Assume
import java.io.File
import java.nio.file.Paths

class DirectoryMappingListTest : HeavyPlatformTestCase() {
  private val BASE_PATH = "/vcs/directoryMappings/"
  private val CVS = "CVSv2"
  private val MOCK = "mock"
  private val MOCK2 = "mock2"

  private lateinit var mappings: NewMappings
  private lateinit var projectRoot: VirtualFile
  private lateinit var rootPath: String

  private lateinit var vcsManager: ProjectLevelVcsManagerImpl

  private lateinit var vcsMock: MockAbstractVcs
  private lateinit var vcsMock2: MockAbstractVcs
  private lateinit var vcsCVS: MockAbstractVcs

  override fun setUpProject() {
    TestLoggerFactory.enableDebugLogging(testRootDisposable,
                                         "#" + NewMappings::class.java.name,
                                         "#" + VcsInitialization::class.java.name)

    val root = FileUtil.toSystemIndependentName(VcsTestUtil.getTestDataPath() + BASE_PATH)

    projectRoot = PsiTestUtil.createTestProjectStructure(getTestName(true), null, root, myFilesToDelete, false)
    rootPath = projectRoot.path

    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get("$rootPath/directoryMappings.ipr"))

    vcsMock = MockAbstractVcs(myProject, MOCK)
    vcsMock2 = MockAbstractVcs(myProject, MOCK2)
    vcsCVS = MockAbstractVcs(myProject, CVS)

    val vcses = AllVcses.getInstance(myProject)
    vcses.registerManually(vcsMock)
    vcses.registerManually(vcsMock2)
    vcses.registerManually(vcsCVS)

    vcsManager = ProjectLevelVcsManager.getInstance(myProject) as ProjectLevelVcsManagerImpl
    mappings = NewMappings(myProject, vcsManager)
    mappings.activateActiveVcses()
    Disposer.register(testRootDisposable, mappings)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
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
      VcsDirectoryMapping(pathsStr[0], MOCK),
      VcsDirectoryMapping(pathsStr[1], MOCK),
      VcsDirectoryMapping(pathsStr[2], MOCK),
      VcsDirectoryMapping(pathsStr[3], MOCK2),
      VcsDirectoryMapping(pathsStr[4], MOCK2),
      VcsDirectoryMapping(pathsStr[5], MOCK2))

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

    mappings.setMapping("$rootPath/a", CVS)
    mappings.setMapping("$rootPath/a-b", MOCK2)
    assertEquals(2, mappings.directoryMappings.size)

    mappings.cleanupMappings()
    assertEquals(2, mappings.directoryMappings.size)
    assertEquals(MOCK2, getVcsFor(childAB))
    assertEquals(CVS, getVcsFor(childA))
  }

  fun testSamePrefixEmpty() {
    val childAB = projectRoot.findChild("a-b")!!

    mappings.setMapping("$rootPath/a", CVS)
    assertNull(getVcsFor(childAB))
  }

  fun testSame() {
    mappings.setMapping("$rootPath/parent/path1", CVS)
    mappings.setMapping("$rootPath\\parent\\path2", CVS)

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
      mappings.setMapping(child, CVS)
      mappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 2, mappings.directoryMappings.size)
    }

    for (child in children) {
      mappings.setMapping(child, CVS)
      assertEquals("cleanup failed: $child", 2, mappings.directoryMappings.size)
    }
  }

  fun testHierarchy() {
    mappings.setMapping("$rootPath/parent", CVS)

    val children = listOf(
      "$rootPath/parent/child1",
      "$rootPath/parent/middle/child2",
      "$rootPath/parent/middle/child3"
    )
    createDirectories(children)

    for (child in children) {
      mappings.setMapping(child, CVS)
      mappings.cleanupMappings()
      assertEquals("cleanup failed: $child", 1, mappings.directoryMappings.size)
    }
  }

  fun testNoneVcsMappings() {
    mappings.setMapping("$rootPath/parent", CVS)

    val children = listOf(
      "$rootPath/parent/child1",
      "$rootPath/parent/middle/child2",
      "$rootPath/parent/middle/child3"
    )
    createDirectories(children)

    mappings.setMapping(children[0], null)
    mappings.cleanupMappings()
    assertEquals("cleanup failed", 2, mappings.directoryMappings.size)

    mappings.setMapping(children[1], "")
    mappings.cleanupMappings()
    assertEquals("cleanup failed", 3, mappings.directoryMappings.size)

    mappings.setMapping(children[2], "Unknown")
    mappings.cleanupMappings()
    assertEquals("cleanup failed", 4, mappings.directoryMappings.size)

    assertEquals(CVS, getVcsFor("$rootPath/parent/some/file".filePath))
    assertEquals(CVS, getVcsFor("$rootPath/parent/middle/file".filePath))
    assertEquals(null, getVcsFor("$rootPath/parent/child1/file".filePath))
    assertEquals(null, getVcsFor("$rootPath/parent/middle/child2".filePath))
    assertEquals(null, getVcsFor("$rootPath/parent/middle/child3".filePath))

    assertEquals("$rootPath/parent".virtualFile, mappings.getMappedRootFor("$rootPath/parent/some/file".filePath)?.root)
    assertEquals("$rootPath/parent/child1".virtualFile, mappings.getMappedRootFor("$rootPath/parent/child1/file".filePath)?.root)
    assertEquals("$rootPath/parent/middle/child2".virtualFile, mappings.getMappedRootFor("$rootPath/parent/middle/child2".filePath)?.root)
    assertEquals("$rootPath/parent/middle/child3".virtualFile, mappings.getMappedRootFor("$rootPath/parent/middle/child3".filePath)?.root)
  }

  fun testNestedInnerCopy() {
    mappings.setMapping("$rootPath/parent", CVS)
    mappings.setMapping("$rootPath/parent/child", MOCK)

    val children = listOf(
      "$rootPath/parent/child1",
      "$rootPath\\parent\\middle\\child2",
      "$rootPath/parent/middle/child3",
      "$rootPath/parent/child/inner"
    )
    val files = createDirectories(children)

    mappings.waitMappedRootsUpdate()

    val awaitedVcsNames = listOf(CVS, CVS, CVS, MOCK)
    for (i in children.indices) {
      val mapping = getMappingFor(files[i])
      assertEquals(awaitedVcsNames[i], mapping?.vcs)
    }
  }

  fun testMappingInFSRoot() {
    val root = VfsUtil.getRootFile(projectRoot)
    mappings.setMapping(root.path, CVS)
    mappings.setMapping(projectRoot.path, MOCK)
    assertEquals(MOCK, getVcsFor(projectRoot))
    assertEquals(CVS, getVcsFor(VcsUtil.getFilePath(root)))
    assertEquals(CVS, getVcsFor(VcsUtil.getFilePath(root, "/some/folder")))
  }

  fun testRootMapping() {
    val roots = listOf(
      "$rootPath/parent/child1",
      "$rootPath/parent/child1/dir/subChild1",
      "$rootPath/parent/child1/dir/subChild2",
      "$rootPath/parent/child1/dir/subChild2/subSubChild",
      "$rootPath/parent/child2",
      "$rootPath/parent/child3"
    )
    createDirectories(roots)

    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }
    assertEquals(6, mappings.getMappingsAsFilesUnderVcs(vcsMock).size)

    assertMappedRoot("$rootPath/parent/file1", null)
    assertMappedRoot("$rootPath/parent/child1/file1", "$rootPath/parent/child1")
    assertMappedRoot("$rootPath/parent/child2/file2", "$rootPath/parent/child2")
    assertMappedRoot("$rootPath/parent/child1/dir/dir/dir/file2", "$rootPath/parent/child1")
    assertMappedRoot("$rootPath/parent/child1/dir/subChild1/file1", "$rootPath/parent/child1/dir/subChild1")
    assertMappedRoot("$rootPath/parent/child1/dir/file1", "$rootPath/parent/child1")

    assertMappedRoot("$rootPath/parent/child1/dir/subChild1", "$rootPath/parent/child1/dir/subChild1", true)
    assertMappedRoot("$rootPath/parent/child1/dir/subChild2", "$rootPath/parent/child1/dir/subChild2", true)
    assertMappedRoot("$rootPath/parent/child2/dir/subChild1", "$rootPath/parent/child2", true)
    assertMappedRoot("$rootPath/parent/child1/dir/subChild2/subSubChild", "$rootPath/parent/child1/dir/subChild2/subSubChild", true)
    assertMappedRoot("$rootPath/parent/child1/dir/subChild2/subSubChild2", "$rootPath/parent/child1/dir/subChild2", true)

    assertMappedRoot("$rootPath/parent/child4/dir/subChild1", null, true)
    assertMappedRoot("$rootPath/parent/child", null, true)
    assertMappedRoot("$rootPath/something/child2", null, true)
    assertMappedRoot(rootPath, null, true)
    assertMappedRoot("/", null, true)
  }

  fun testRootMappingCaseSensitive() {
    Assume.assumeTrue(SystemInfo.isFileSystemCaseSensitive)

    val roots = listOf(
      "$rootPath/parent/Child",
      "$rootPath/parent/CHILD/dir",
      "$rootPath/parent/child",
      "$rootPath/parent/child/dir/subChild",
      "$rootPath/parent/child/dir/subChild/subSubChild"
    )
    createDirectories(roots)

    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }
    assertEquals(5, mappings.getMappingsAsFilesUnderVcs(vcsMock).size)

    assertMappedRoot("$rootPath/parent/file1", null)

    assertMappedRoot("$rootPath/parent/Child/file1", "$rootPath/parent/Child")
    assertMappedRoot("$rootPath/parent/Child/dir/file1", "$rootPath/parent/Child")
    assertMappedRoot("$rootPath/parent/Child/dir/subChild", "$rootPath/parent/Child", true)
    assertMappedRoot("$rootPath/parent/Child/dir/subChild/subSubChild/file", "$rootPath/parent/Child")

    assertMappedRoot("$rootPath/parent/CHILD/file1", null)
    assertMappedRoot("$rootPath/parent/CHILD/dir/file1", "$rootPath/parent/CHILD/dir")
    assertMappedRoot("$rootPath/parent/CHILD/dir/subChild", "$rootPath/parent/CHILD/dir", true)
    assertMappedRoot("$rootPath/parent/CHILD/dir/subChild/subSubChild/file", "$rootPath/parent/CHILD/dir")

    assertMappedRoot("$rootPath/parent/child/file1", "$rootPath/parent/child")
    assertMappedRoot("$rootPath/parent/child/dir/file1", "$rootPath/parent/child")
    assertMappedRoot("$rootPath/parent/child/dir/subChild", "$rootPath/parent/child/dir/subChild", true)
    assertMappedRoot("$rootPath/parent/child/dir/subChild/subSubChild/file", "$rootPath/parent/child/dir/subChild/subSubChild")
  }

  fun testRootMappingCaseInsensitive() {
    Assume.assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    val roots = listOf(
      "$rootPath/parent/Child",
      "$rootPath/parent/child",
      "$rootPath/parent/CHILD",
      "$rootPath/parent/CHILD/dir",
      "$rootPath/parent/child/dir/subChild",
      "$rootPath/parent/child/dir/subChild/subSubChild"
    )
    createDirectories(roots)

    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }
    assertEquals(4, mappings.getMappingsAsFilesUnderVcs(vcsMock).size)

    assertMappedRoot("$rootPath/parent/file1", null)

    assertMappedRoot("$rootPath/parent/Child/file1", "$rootPath/parent/child")
    assertMappedRoot("$rootPath/parent/Child/dir/file1", "$rootPath/parent/child/dir")
    assertMappedRoot("$rootPath/parent/Child/dir/subChild", "$rootPath/parent/child/dir/subChild", true)
    assertMappedRoot("$rootPath/parent/Child/dir/subChild/subSubChild/file", "$rootPath/parent/child/dir/subChild/subSubChild")

    assertMappedRoot("$rootPath/parent/CHILD/file1", "$rootPath/parent/child")
    assertMappedRoot("$rootPath/parent/CHILD/dir/file1", "$rootPath/parent/child/dir")
    assertMappedRoot("$rootPath/parent/CHILD/dir/subChild", "$rootPath/parent/child/dir/subChild", true)
    assertMappedRoot("$rootPath/parent/CHILD/dir/subChild/subSubChild/file", "$rootPath/parent/child/dir/subChild/subSubChild")

    assertMappedRoot("$rootPath/parent/child/file1", "$rootPath/parent/child")
    assertMappedRoot("$rootPath/parent/child/dir/file1", "$rootPath/parent/child/dir")
    assertMappedRoot("$rootPath/parent/child/dir/subChild", "$rootPath/parent/child/dir/subChild", true)
    assertMappedRoot("$rootPath/parent/child/dir/subChild/subSubChild/file", "$rootPath/parent/child/dir/subChild/subSubChild")
  }

  fun testPerformanceFewRootsFilePaths() {
    val roots = listOf(
      "$rootPath/parent/module1",
      "$rootPath/parent"
    )
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = listOf(
      "$rootPath/parent",
      "$rootPath/parent/module1",
      "$rootPath/parent/module1/foo/bar/dir/dir",
      "$rootPath/parent/non_existent/some/path"
    ).map { it.filePath }

    PlatformTestUtil.startPerformanceTest("NewMappings few roots FilePaths", 1000) {
      for (i in 0..20000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  fun testPerformanceManyRootsFilePaths() {
    val roots = (0..1000).map { "$rootPath/parent/module$it" } +
                "$rootPath/parent"
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = listOf(
      "$rootPath/parent",
      "$rootPath/parent/module1",
      "$rootPath/parent/module100/foo",
      "$rootPath/parent/module500/foo/bar/dir/dir",
      "$rootPath/non_existent/some/path"
    ).map { it.filePath }

    PlatformTestUtil.startPerformanceTest("NewMappings many roots FilePaths", 1000) {
      for (i in 0..20000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  fun testPerformanceNestedRootsFilePaths() {
    var path = "$rootPath/parent"
    val roots = mutableListOf<String>()
    for (i in 0..200) {
      roots.add(path)
      path += "/dir"
    }
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = listOf(
      "$rootPath/parent",
      "$rootPath/parent/" + "dir/".repeat(50),
      "$rootPath/parent/" + "dir/".repeat(150) + "some/other/dirs",
      "$rootPath/parent/" + "dir/".repeat(180),
      "$rootPath/parent/" + "dir/".repeat(220)
    ).map { it.filePath }

    PlatformTestUtil.startPerformanceTest("NewMappings nested roots FilePaths", 1000) {
      for (i in 0..2000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  fun testPerformanceFewRootsVirtualFiles() {
    val roots = listOf(
      "$rootPath/parent/module1",
      "$rootPath/parent"
    )
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = createDirectories(listOf(
      "$rootPath/parent",
      "$rootPath/parent/module1",
      "$rootPath/parent/module1/foo/bar/dir/dir",
      "$rootPath/parent/non_existent/some/path"
    ))

    PlatformTestUtil.startPerformanceTest("NewMappings few roots VirtualFiles", 500) {
      for (i in 0..60000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  fun testPerformanceManyRootsVirtualFiles() {
    val roots = (0..1000).map { "$rootPath/parent/module$it" } +
                "$rootPath/parent"
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = createDirectories(listOf(
      "$rootPath/parent",
      "$rootPath/parent/module1",
      "$rootPath/parent/module100/foo",
      "$rootPath/parent/module500/foo/bar/dir/dir",
      "$rootPath/non_existent/some/path"
    ))

    PlatformTestUtil.startPerformanceTest("NewMappings many roots VirtualFiles", 500) {
      for (i in 0..80000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  fun testPerformanceNestedRootsVirtualFiles() {
    var path = "$rootPath/parent"
    val roots = mutableListOf<String>()
    for (i in 0..200) {
      roots.add(path)
      path += "/dir"
    }
    createDirectories(roots)
    mappings.directoryMappings = roots.map { VcsDirectoryMapping(it, MOCK) }

    val toCheck = createDirectories(listOf(
      "$rootPath/parent",
      "$rootPath/parent/" + "dir/".repeat(50),
      "$rootPath/parent/" + "dir/".repeat(150) + "some/other/dirs",
      "$rootPath/parent/" + "dir/".repeat(180),
      "$rootPath/parent/" + "dir/".repeat(220)
    ))

    PlatformTestUtil.startPerformanceTest("NewMappings nested roots VirtualFiles", 500) {
      for (i in 0..15000) {
        for (filePath in toCheck) {
          mappings.getMappedRootFor(filePath)
        }
      }
    }.assertTiming()
  }

  private fun createDirectories(paths: List<String>): List<VirtualFile> {
    return paths.map { createFile(it, isDirectory = true) }
  }

  private fun createFile(path: String, isDirectory: Boolean = false): VirtualFile {
    // passed path contains backslash - that's why toSystemDependentName is used here
    val file = File(FileUtil.toSystemDependentName(path))
    if (isDirectory) {
      val created = file.exists() && file.isDirectory || file.mkdirs()
      assertTrue("Can't create directory: $file", created)
    }
    else {
      createFile(file.parent, isDirectory = true)
      val created = file.exists() && file.isFile || file.createNewFile()
      assertTrue("Can't create file: $file", created)
    }
    if (path != "/" && !FileUtil.isAncestor(FileUtil.getTempDirectory(), file.path, false)) {
      myFilesToDelete.add(file.toPath())
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
           ?: throw IllegalStateException("Cannot find virtual file: $file")
  }

  private fun getVcsFor(file: VirtualFile): String? {
    val root = mappings.getMappedRootFor(file)
    return root?.vcs?.name
  }

  private fun getVcsFor(file: FilePath): String? {
    val root = mappings.getMappedRootFor(file)
    return root?.vcs?.name
  }

  private fun getMappingFor(file: VirtualFile): VcsDirectoryMapping? {
    val root = mappings.getMappedRootFor(file)
    return root?.mapping
  }

  private fun assertMappedRoot(path: String, expectedRoot: String?, isDirectory: Boolean = false) {
    createFile(path, isDirectory)
    val root1 = mappings.getMappedRootFor(path.filePath)
    val root2 = mappings.getMappedRootFor(path.virtualFile)
    assertEquals(expectedRoot?.virtualFile, root1?.root)
    assertEquals(expectedRoot?.virtualFile, root2?.root)
  }

  private val String.virtualFile: VirtualFile
    get() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(FileUtil.toSystemDependentName(this)))!!

  private val String.filePath: FilePath
    get() = VcsUtil.getFilePath(File(FileUtil.toSystemDependentName(this)))
}