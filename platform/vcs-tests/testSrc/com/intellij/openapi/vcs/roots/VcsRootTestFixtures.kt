// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.test.VcsPlatformTestContext
import com.intellij.vcs.test.vcsPlatformContextFixture
import java.io.File

internal const val DOT_MOCK = ".mock"

/**
 * A [VcsPlatformTestContext] with a registered [MockAbstractVcs], its [MockRootChecker], and the module "foo" in the project.
 */
internal interface VcsRootTestContext : VcsPlatformTestContext {
  val vcs: MockAbstractVcs
  val rootChecker: MockRootChecker
  val rootModule: Module
}

/**
 * Creates the module "foo" in the project directory with [createVcs] as the registered VCS.
 */
internal fun vcsRootContextFixture(
  createVcs: (Project) -> MockAbstractVcs = ::MockAbstractVcs,
): TestFixture<VcsRootTestContext> = testFixture {
  val platformContext = vcsPlatformContextFixture().init()
  val project = platformContext.project

  platformContext.cd(platformContext.projectRoot)
  val rootModule = createModule(project, "foo")
  platformContext.mkdir("repository")
  platformContext.projectRoot.refresh(false, true)

  val vcs = createVcs(project)
  val rootChecker = MockRootChecker(vcs)
  val extensionDisposable = Disposer.newDisposable("MockRootChecker")
  VcsRootChecker.EXTENSION_POINT_NAME.point.registerExtension(rootChecker, extensionDisposable)
  platformContext.vcsManager.registerVcs(vcs)

  val context = object : VcsRootTestContext, VcsPlatformTestContext by platformContext {
    override val vcs = vcs
    override val rootChecker = rootChecker
    override val rootModule = rootModule
  }
  initialized(context) {
    runAll(
      { platformContext.vcsManager.unregisterVcs(vcs) },
      { Disposer.dispose(extensionDisposable) },
    )
  }
}

private suspend fun createModule(project: Project, name: String): Module {
  val moduleFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).toNioPath().resolve("$name.iml")
  val module = edtWriteAction {
    ModuleManager.getInstance(project).newModule(moduleFile, EmptyModuleType.EMPTY_MODULE)
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  return module
}

/**
 * Creates the necessary temporary directories in the filesystem with empty ".mock" directories for given roots.
 * And creates an instance of the project.
 */
internal fun VcsRootTestContext.initProject(vcsRootConfiguration: VcsRootConfiguration) {
  createDirs(vcsRootConfiguration.vcsRoots)
  val contentRoots = vcsRootConfiguration.contentRoots
  createProjectStructure(project, contentRoots)
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

private fun VcsRootTestContext.createProjectStructure(project: Project, paths: Collection<String>) {
  for (path in paths) {
    cd(PlatformTestUtil.getOrCreateProjectBaseDir(project).path)
    val f = File(project.basePath, path)
    f.mkdirs()
    StandardFileSystems.local().refreshAndFindFileByPath(f.absolutePath)
  }
  projectRoot.refresh(false, true)
}

private fun VcsRootTestContext.createDirs(mockRoots: Collection<String>) {
  if (mockRoots.isEmpty()) {
    return
  }

  val baseDir = VfsUtilCore.virtualToIoFile(PlatformTestUtil.getOrCreateProjectBaseDir(project))
  val maxDepth = findMaxDepthAboveProject(mockRoots)
  val projectDir = createChild(baseDir, maxDepth - 1)
  cd(projectDir.path)
  for (path in mockRoots) {
    val mockDir = File(File(projectDir, path), DOT_MOCK)
    mockDir.mkdirs()
    StandardFileSystems.local().refreshAndFindFileByPath(mockDir.absolutePath)
  }
}

private fun createChild(base: File, depth: Int): File {
  var dir = base
  if (depth < 0) {
    return dir
  }
  repeat(depth) {
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
