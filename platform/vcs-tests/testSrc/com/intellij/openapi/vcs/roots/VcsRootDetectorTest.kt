// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.roots

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.mkdir
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.VcsTestUtil.assertEqualCollections
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase
import one.util.streamex.StreamEx
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File
import java.util.Arrays.asList
import java.util.Collections.emptyList

class VcsRootDetectorTest : VcsRootBaseTest() {

  fun testNoRootsInProject() {
    doTest(VcsRootConfiguration(), null)
  }

  fun testProjectUnderSingleMockRoot() {
    doTest(VcsRootConfiguration().vcsRoots("."), projectRoot, ".")
  }

  fun testProjectWithMockRootUnderIt() {
    cd(projectRoot)
    mkdir("src")
    mkdir(PathMacroUtil.DIRECTORY_STORE_NAME)
    doTest(VcsRootConfiguration().vcsRoots("community"), projectRoot, "community")
  }

  fun testProjectWithAllSubdirsUnderMockRootShouldStillBeNotFullyControlled() {
    val dirNames = arrayOf(PathMacroUtil.DIRECTORY_STORE_NAME, "src", "community")
    doTest(VcsRootConfiguration().vcsRoots(*dirNames), projectRoot, *dirNames)
  }

  fun testProjectUnderVcsAboveIt() {
    val subdir = "insideRepo"
    cd(myRepository)
    mkdir(subdir)
    val vfile = myRepository.findChild(subdir)
    doTest(VcsRootConfiguration().vcsRoots(myRepository.name), vfile, myRepository.name
    )
  }

  fun testIDEAProject() {
    val names = arrayOf("community", "contrib", ".")
    doTest(VcsRootConfiguration().vcsRoots(*names), projectRoot, *names)
  }

  fun testOneAboveAndOneUnder() {
    val names = arrayOf(myRepository.name + "/community", ".")
    doTest(VcsRootConfiguration().vcsRoots(*names), myRepository, *names)
  }

  fun testOneAboveAndOneForProjectShouldShowOnlyProjectRoot() {
    val names = arrayOf(myRepository.name, ".")
    doTest(VcsRootConfiguration().vcsRoots(*names), myRepository, myRepository.name)
  }

  fun testDontDetectAboveIfProjectIsIgnoredThere() {
    rootChecker.setIgnored(myRepository)
    assertTrue(File(testRoot, DOT_MOCK).mkdir())
    doTest(VcsRootConfiguration().vcsRoots(testRoot.path), myRepository)
  }

  fun testOneAboveAndSeveralUnderProject() {
    val names = arrayOf(".", myRepository.name + "/community", myRepository.name + "/contrib")
    doTest(VcsRootConfiguration().vcsRoots(*names), myRepository, *names)
  }

  fun testMultipleAboveShouldBeDetectedAsOneAbove() {
    val subdir = "insideRepo"
    cd(myRepository)
    mkdir(subdir)
    val vfile = myRepository.findChild(subdir)
    doTest(VcsRootConfiguration().vcsRoots(".", myRepository.name), vfile, myRepository.name)
  }

  fun testUnrelatedRootShouldNotBeDetected() {
    doTest(VcsRootConfiguration().vcsRoots("another"), myRepository)
  }

  fun testLinkedSourceRootAloneShouldBeDetected() {
    val linkedRoot = "linked_root"
    val linkedRootDir = File(testRoot, linkedRoot)
    assertTrue(File(linkedRootDir, DOT_MOCK).mkdirs())
    rootModel.addContentEntry(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(linkedRootDir)!!)

    val roots = detect(projectRoot)

    assertEqualCollections(StreamEx.of(roots).map { it -> it.path!!.path }.toList(),
                           listOf(toSystemIndependentName(linkedRootDir.path)))
  }

  fun testLinkedSourceRootAndProjectRootShouldBeDetected() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "linked_root")
      .contentRoots("linked_root")
    doTest(vcsRootConfiguration, projectRoot, ".", "linked_root")
  }

  fun testLinkedSourceBelowMockRoot() {
    val vcsRootConfiguration = VcsRootConfiguration().contentRoots("linked_root/src")
      .vcsRoots(".", "linked_root")
    doTest(vcsRootConfiguration, projectRoot, ".", "linked_root")
  }

  // This is a test of performance optimization via limitation: don't scan deep though the whole VFS, i.e. don't detect deep roots
  fun testDontScanDeeperThan2LevelsBelowAContentRoot() {
    Registry.get("vcs.root.detector.folder.depth").setValue(2, testRootDisposable)
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("community", "content_root/lev1", "content_root2/lev1/lev2/lev3")
      .contentRoots("content_root")
    doTest(vcsRootConfiguration,
           projectRoot, "community", "content_root/lev1")
  }

  fun testDontScanExcludedDirs() {
    val vcsRootConfiguration = VcsRootConfiguration()
      .contentRoots("community", "excluded")
      .vcsRoots("community", "excluded/lev1")
    setUp(vcsRootConfiguration, projectRoot)

    val excludedFolder = projectRoot.findChild("excluded")
    TestCase.assertNotNull(excludedFolder)
    markAsExcluded(excludedFolder!!)

    val vcsRoots = detect(projectRoot)
    assertRoots(listOf("community"), getPaths(vcsRoots))
  }

  private fun assertRoots(expectedRelativePaths: Collection<String>, actual: Collection<String>) {
    assertEqualCollections(actual, toAbsolute(expectedRelativePaths, myProject))
  }

  private fun markAsExcluded(dir: VirtualFile) {
    ModuleRootModificationUtil.updateExcludedFolders(rootModel.module, dir, emptyList(), listOf(dir.url))
  }

  private fun detect(startDir: VirtualFile?): Collection<VcsRoot> {
    return ServiceManager.getService(myProject, VcsRootDetector::class.java).detect(startDir)
  }

  private fun doTest(vcsRootConfiguration: VcsRootConfiguration,
                     startDir: VirtualFile?,
                     vararg expectedPaths: String) {
    setUp(vcsRootConfiguration, startDir)
    val vcsRoots = detect(startDir)
    assertRoots(asList(*expectedPaths), getPaths(
      ContainerUtil.filter(vcsRoots) { root ->
        assert(root.vcs != null)
        root.vcs!!.keyInstanceMethod == vcs.keyInstanceMethod
      }
    ))
  }

  private fun setUp(vcsRootConfiguration: VcsRootConfiguration, startDir: VirtualFile?) {
    initProject(vcsRootConfiguration)
    startDir?.refresh(false, true)
  }

  private fun toAbsolute(relPaths: Collection<String>, project: Project): Collection<String> {
    return relPaths.map {
      toSystemIndependentName(File(project.basePath, it).canonicalPath)
    }
  }

  private fun getPaths(files: Collection<VcsRoot>): Collection<String> {
    return ContainerUtil.map(files) { root ->
      val file = root.path!!
      toSystemIndependentName(file.path)
    }
  }
}
