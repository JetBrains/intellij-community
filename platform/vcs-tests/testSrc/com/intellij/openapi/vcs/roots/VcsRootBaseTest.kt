// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.mkdir
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.test.VcsPlatformTest
import java.io.File

internal const val DOT_MOCK = ".mock"

abstract class VcsRootBaseTest : VcsPlatformTest() {
  protected lateinit var vcs: MockAbstractVcs

  protected lateinit var rootChecker: MockRootChecker
  protected lateinit var rootModule: Module

  private val extensionPoint: ExtensionPoint<VcsRootChecker>
    get() = VcsRootChecker.EXTENSION_POINT_NAME.point

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    cd(projectRoot)
    rootModule = doCreateRealModuleIn("foo", myProject, EmptyModuleType.getInstance())
    mkdir("repository")
    projectRoot.refresh(false, true)

    vcs = MockAbstractVcs(myProject)
    rootChecker = MockRootChecker(vcs)
    extensionPoint.registerExtension(rootChecker, testRootDisposable)
    vcsManager.registerVcs(vcs)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      vcsManager.unregisterVcs(vcs)
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
      runInEdtAndWait {
        for (root in contentRoots) {
          val f = projectRoot.findFileByRelativePath(root)
          if (f != null) {
            PsiTestUtil.addContentRoot(rootModule, f)
          }
        }
      }
    }
  }

  private fun createProjectStructure(project: Project, paths: Collection<String>) {
    for (path in paths) {
      cd(PlatformTestUtil.getOrCreateProjectBaseDir(project).path)
      val f = File(project.basePath, path)
      f.mkdirs()
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)
    }
    projectRoot.refresh(false, true)
  }

  private fun createDirs(mockRoots: Collection<String>) {
    if (mockRoots.isEmpty()) {
      return
    }

    val baseDir = VfsUtilCore.virtualToIoFile(PlatformTestUtil.getOrCreateProjectBaseDir(getProject()))
    val maxDepth = findMaxDepthAboveProject(mockRoots)
    val projectDir = createChild(baseDir, maxDepth - 1)
    cd(projectDir.path)
    for (path in mockRoots) {
      val mockDir = File(File(projectDir, path), DOT_MOCK)
      mockDir.mkdirs()
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mockDir)
    }
  }

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
  private fun findMaxDepthAboveProject(paths: Collection<String>): Int {
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
