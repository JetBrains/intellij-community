// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.roots

import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsTestUtil.assertEqualCollections
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File
import java.util.Collections.emptyList

private const val DOT_IDEA = PathMacroUtil.DIRECTORY_STORE_NAME

class VcsRootDetectorTest : VcsRootBaseTest() {

  fun `test no roots`() {
    expect(emptyList())
  }

  fun `test project dir is the only root`() {
    projectRoot.initRepository()
    expect(projectRoot)
  }

  fun `test root under project`() {
    val roots = createVcsRoots(listOf("src"))
    expect(roots)
  }

  fun `test 3 roots under project`() {
    val vcsRoots = createVcsRoots(listOf(DOT_IDEA, "src", "community"))
    expect(vcsRoots)
  }

  fun `test vcs root above project`() {
    testRoot.initRepository()
    expect(testRootFile)
  }

  fun `test one main and two nested sibling roots`() {
    projectRoot.initRepository()
    val roots = createVcsRoots("community", "contrib")
    expect(roots + projectRoot)
  }

  fun `test one above and one under`() {
    testRoot.initRepository()
    val roots = createVcsRoots("subroot")
    expect(roots + testRootFile)
  }

  fun `test one above and one for project should show only project root`() {
    testRoot.initRepository()
    projectRoot.initRepository()
    expect(projectRoot)
  }

  fun `test dont detect above if project is ignored there`() {
    rootChecker.setIgnored(projectRoot)
    testRoot.initRepository()
    expect(emptyList())
  }

  fun `test one above and several under project`() {
    testRoot.initRepository()
    projectRoot.initRepository()
    val roots = createVcsRoots("community", "contrib")
    expect(roots + projectRoot)
  }

  fun `test unrelated root should not be detected`() {
    val file = File(testRoot, "another")
    assertTrue(file.mkdir())
    file.initRepository()
    expect(emptyList())
  }

  fun `test linked source root alone should be detected`() {
    val linkedRoot = File(testRoot, "linked_root").mkd()
    val vf = linkedRoot.toVirtualFile()
    linkedRoot.initRepository()
    PsiTestUtil.addContentRoot(rootModule, vf)
    expect(vf)
  }

  fun `test linked source root and project root should be detected`() {
    val linkedRoot = File(testRoot, "linked_root").mkd()
    val vf = linkedRoot.toVirtualFile()
    linkedRoot.initRepository()
    PsiTestUtil.addContentRoot(rootModule, vf)

    projectRoot.initRepository()

    expect(listOf(vf, projectRoot))
  }

  fun `test two nested roots`() {
    val roots = createVcsRoots("community", "content_root/subroot")
    PsiTestUtil.addContentRoot(rootModule, projectRoot.findChild("content_root")!!)

    expect(roots)
  }

  fun `test dont scan deeper than2LevelsBelowAContentRoot`() {
    Registry.get("vcs.root.detector.folder.depth").setValue(2, testRootDisposable)

    val roots = createVcsRoots("community", "content_root/lev1", "content_root2/lev1/lev2/lev3")
    PsiTestUtil.addContentRoot(rootModule, projectRoot.findChild("content_root")!!)

    expect(roots.subList(0, 2))
  }

  fun `test dont scan excluded dirs`() {
    val roots = createVcsRoots("community", "excluded/lev1")

    val excludedFolder = projectRoot.findChild("excluded")
    assertNotNull(excludedFolder)
    markAsExcluded(excludedFolder!!)

    expect(roots[0])
  }

  private fun createVcsRoots(vararg relativePaths: String) = createVcsRoots(listOf(*relativePaths))

  private fun createVcsRoots(relativePaths: Collection<String>): List<VirtualFile> {
    return relativePaths.map {
      val file = File(projectPath, it)
      assertTrue(file.mkdirs())
      file.initRepository()
      val vf = file.toVirtualFile()
      PsiTestUtil.addContentRoot(rootModule, vf)
      vf
    }
  }

  private fun expect(vararg expectedRoots: VirtualFile) = expect(listOf(*expectedRoots))

  private fun expect(expectedRoots: Collection<VirtualFile>) {
    val detectedRoots = project.service<VcsRootDetector>().detect().map { it.path }
    assertEqualCollections(detectedRoots, expectedRoots)
  }

  private fun File.toVirtualFile() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this)!!

  private fun VirtualFile.initRepository() = VfsUtil.virtualToIoFile(this).initRepository()

  private fun File.initRepository() : VirtualFile {
    val file = File(this, DOT_MOCK)
    assertTrue(file.mkdir())
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
  }

  private fun File.mkd() : File {
    assertTrue(this.mkdir())
    return this
  }

  private fun markAsExcluded(dir: VirtualFile) {
    ModuleRootModificationUtil.updateExcludedFolders(rootModule, dir, emptyList(), listOf(dir.url))
  }
}
