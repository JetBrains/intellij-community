// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.openapi.roots.impl.RootModelImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.mkdir
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.vcs.test.VcsPlatformTest
import java.io.File
import java.io.IOException


internal val DOT_MOCK = ".mock"

abstract class VcsRootBaseTest : VcsPlatformTest() {
  protected lateinit var myVcs: MockAbstractVcs
  protected lateinit var myVcsName: String
  protected lateinit var myRepository: VirtualFile

  protected lateinit var myRootChecker: MockRootChecker
  protected lateinit var myRootModel: RootModelImpl

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    cd(projectRoot)
    val module = doCreateRealModuleIn("foo", myProject, EmptyModuleType.getInstance())
    myRootModel = (ModuleRootManager.getInstance(module) as ModuleRootManagerImpl).rootModel
    mkdir("repository")
    projectRoot.refresh(false, true)
    myRepository = projectRoot.findChild("repository")!!

    myVcs = MockAbstractVcs(myProject)
    val point = extensionPoint
    myRootChecker = MockRootChecker(myVcs)
    point.registerExtension(myRootChecker)
    vcsManager.registerVcs(myVcs)
    myVcsName = myVcs.name
    myRepository.refresh(false, true)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      extensionPoint.unregisterExtension(myRootChecker)
      vcsManager.unregisterVcs(myVcs)
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * Creates the necessary temporary directories in the filesystem with empty ".mock" directories for given roots.
   * And creates an instance of the project.
   *
   * @param mockRoots path to actual .mock roots, relative to the project dir.
   */
  internal fun initProject(vcsRootConfiguration: VcsRootConfiguration) {
    createDirs(vcsRootConfiguration.vcsRoots)
    val contentRoots = vcsRootConfiguration.contentRoots
    createProjectStructure(myProject, contentRoots)
    if (!contentRoots.isEmpty()) {
      EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
        for (root in contentRoots) {
          val f = projectRoot.findFileByRelativePath(root)
          if (f != null) {
            myRootModel.addContentEntry(f)
          }
        }
      })
    }
  }

  internal fun createProjectStructure(project: Project, paths: Collection<String>) {
    for (path in paths) {
      cd(project.baseDir.path)
      val f = File(project.basePath, path)
      f.mkdirs()
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)
    }
    projectRoot.refresh(false, true)
  }

  @Throws(IOException::class)
  private fun createDirs(mockRoots: Collection<String>) {
    val baseDir: File
    if (mockRoots.isEmpty()) {
      return
    }

    baseDir = VfsUtilCore.virtualToIoFile(myProject.baseDir)
    val maxDepth = findMaxDepthAboveProject(mockRoots)
    val projectDir = createChild(baseDir, maxDepth - 1)
    cd(projectDir.path)
    for (path in mockRoots) {
      val mockDir = File(File(projectDir, path), DOT_MOCK)
      mockDir.mkdirs()
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mockDir)
    }
  }

  private val extensionPoint = Extensions.getRootArea().getExtensionPoint(VcsRootChecker.EXTENSION_POINT_NAME)

  @Throws(IOException::class)
  private fun createChild(base: File, depth: Int): File {
    var dir = base
    if (depth < 0) {
      return dir
    }
    for (i in 0 until depth) {
      dir = FileUtil.createTempDirectory(dir, "grdt", null)
    }
    return dir
  }

  // Assuming that there are no ".." inside the path - only in the beginning
  internal fun findMaxDepthAboveProject(paths: Collection<String>): Int {
    var max = 0
    for (path in paths) {
      val splits = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      var count = 0
      for (split in splits) {
        if (split == "..") {
          count++
        }
      }
      if (count > max) {
        max = count
      }
    }
    return max
  }
}
