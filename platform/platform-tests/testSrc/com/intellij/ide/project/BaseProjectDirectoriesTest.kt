// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.project

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.BaseProjectDirectoriesDiff
import com.intellij.openapi.project.BaseProjectDirectoriesListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class BaseProjectDirectoriesTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `add remove content root`() {
    val module = projectModel.createModule()
    val listener = Listener(module)

    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    checkBaseDirectories()

    ModuleRootModificationUtil.addContentRoot(module, root1)
    checkBaseDirectories(root1)
    listener.checkDiff(setOf(), setOf(root1))

    ModuleRootModificationUtil.addContentRoot(module, root2)
    checkBaseDirectories(root1, root2)
    listener.checkDiff(setOf(), setOf(root2))

    PsiTestUtil.removeContentEntry(module, root1)
    checkBaseDirectories(root2)
    listener.checkDiff(setOf(root1), setOf())

    PsiTestUtil.removeContentEntry(module, root2)
    listener.checkDiff(setOf(root2), setOf())

    checkBaseDirectories()
  }

  @Test
  fun `add inner content root`() {
    val module = projectModel.createModule()
    val listener = Listener(module)

    val root = projectModel.baseProjectDir.newVirtualDirectory("root")
    ModuleRootModificationUtil.addContentRoot(module, root)

    checkBaseDirectories(root)
    listener.checkDiff(setOf(), setOf(root))

    val inner = projectModel.baseProjectDir.newVirtualDirectory("root/inner")
    ModuleRootModificationUtil.addContentRoot(module, inner)
    checkBaseDirectories(root)
    listener.assertNoEventsAfterLastChecking()
  }

  @Test
  fun `add remove outer content root`() {
    val module = projectModel.createModule()
    val listener = Listener(module)

    val outer = projectModel.baseProjectDir.newVirtualDirectory("root")
    val inner = projectModel.baseProjectDir.newVirtualDirectory("root/inner")
    ModuleRootModificationUtil.addContentRoot(module, inner)
    checkBaseDirectories(inner)
    listener.checkDiff(setOf(), setOf(inner))

    ModuleRootModificationUtil.addContentRoot(module, outer)
    checkBaseDirectories(outer)
    listener.checkDiff(setOf(inner), setOf(outer))

    PsiTestUtil.removeContentEntry(module, outer)
    checkBaseDirectories(inner)
    listener.checkDiff(setOf(outer), setOf(inner))
  }

  @Test
  fun `find content root for a file`() {
    val module = projectModel.createModule()
    val root = projectModel.baseProjectDir.newVirtualDirectory("root")
    ModuleRootModificationUtil.addContentRoot(module, root)
    checkBaseDirectories(root)

    val dir = VfsTestUtil.createDir(root, "dir")
    val fileInDir = VfsTestUtil.createFile(dir, "file.txt")

    val service = BaseProjectDirectories.getInstance(projectModel.project)
    assertSame(root, service.getBaseDirectoryFor(root))
    assertSame(root, service.getBaseDirectoryFor(dir))
    assertSame(root, service.getBaseDirectoryFor(fileInDir))

    val externalDir = projectModel.baseProjectDir.newVirtualDirectory("extDir")
    val externalFile = VfsTestUtil.createFile(externalDir, "file.txt")
    assertNull(service.getBaseDirectoryFor(externalDir))
    assertNull(service.getBaseDirectoryFor(externalFile))
  }

  private fun checkBaseDirectories(vararg files: VirtualFile) {
    waitUntilChangesAreApplied(projectModel.project)

    assertEquals(files.toSet(), projectModel.project.getBaseDirectories())
  }

  private class Listener(module: com.intellij.openapi.module.Module) {

    private val project = module.project
    private val diffs = mutableListOf<BaseProjectDirectoriesDiff>()
    private var lastCheckDiffsCounter = 0

    init {
      BaseProjectDirectories.getInstance(project)
      project.messageBus.connect(module).subscribe(BaseProjectDirectories.TOPIC, object : BaseProjectDirectoriesListener {
        override fun changed(project: Project, diff: BaseProjectDirectoriesDiff) {
          diffs.add(diff)
        }
      })
    }

    fun checkDiff(removed: Set<VirtualFile>, added: Set<VirtualFile>) {
      waitUntilChangesAreApplied(project)

      assert(diffs.isNotEmpty()) { "No diff changes captured" }
      lastCheckDiffsCounter = diffs.size

      val lastDiff = diffs.last()
      assertEquals(removed.toSet(), lastDiff.removed)
      assertEquals(added.toSet(), lastDiff.added)
    }

    fun assertNoEventsAfterLastChecking() {
      assert(diffs.size == lastCheckDiffsCounter) { "Diff event(s) was fired but was not expected" }
    }
  }
}

private fun waitUntilChangesAreApplied(project: Project) {
  Thread.sleep(30)
  while (BaseProjectDirectories.getInstance(project).isProcessing) {
    Thread.sleep(10)
  }
}
