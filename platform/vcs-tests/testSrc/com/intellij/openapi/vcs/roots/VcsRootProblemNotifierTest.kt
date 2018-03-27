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


package com.intellij.openapi.vcs.roots

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.roots.VcsRootBaseTest.DOT_MOCK
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcsUtil.VcsUtil.getFilePath
import java.io.File

class VcsRootProblemNotifierTest : VcsPlatformTest() {

  private lateinit var checker: VcsRootChecker
  private lateinit var vcs: MockAbstractVcs
  private lateinit var rootProblemNotifier: VcsRootProblemNotifier

  override fun setUp() {
    super.setUp()

    vcs = MockAbstractVcs(myProject)
    checker = MockRootChecker(vcs)
    getExtensionPoint().registerExtension(checker)
    vcsManager.registerVcs(vcs)

    rootProblemNotifier = VcsRootProblemNotifier.getInstance(myProject)
  }

  override fun tearDown() {
    try {
      if (wasInit { checker }) getExtensionPoint().unregisterExtension(checker)
      if (wasInit { vcs }) vcsManager.unregisterVcs(vcs)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#com.intellij.openapi.vcs.roots")

  fun `test root is added automatically in simple case`() {
    assertTrue(File(projectPath, DOT_MOCK).mkdir())

    VcsRootProblemNotifier.getInstance(myProject).rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)
  }

  fun `test nothing is added automatically if two roots detected`() {
    val subRoot = createNestedRoots()

    rootProblemNotifier.rescanAndNotifyIfNeeded()

    assertFalse("No roots should be auto-added since it is not the simple case", vcsManager.hasAnyMappings())
    assertSuccessfulNotification("Unregistered VCS roots detected","""
      The following directories are roots of VCS repositories, but they are not registered in the Settings:
      ${toSystemDependentName(projectPath)}
      ${toSystemDependentName(subRoot.path)}
      <a>Add roots</a> <a>Configure</a> <a>Ignore</a>
      """.trimIndent())
  }

  // IDEA-168690
  fun `test root is not added back if explicitly removed`() {
    assertTrue(File(projectPath, DOT_MOCK).mkdir())
    vcsManager.setDirectoryMapping(projectPath, vcs.name)
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)

    val mapping = vcsManager.getDirectoryMappingFor(getFilePath(projectRoot))
    vcsManager.removeDirectoryMapping(mapping)
    VcsConfiguration.getInstance(myProject).addIgnoredUnregisteredRoots(listOf(projectPath))

    rootProblemNotifier.rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertFalse("The root shouldn't be auto-added after explicit removal", vcsManager.hasAnyMappings())
  }

  // IDEA-CR-18592
  fun `test single root is not added automatically if there is ignored root`() {
    val subRoot = createNestedRoots()
    VcsConfiguration.getInstance(myProject).addIgnoredUnregisteredRoots(listOf(FileUtil.toSystemIndependentName(subRoot.path)))

    rootProblemNotifier.rescanAndNotifyIfNeeded()

    assertFalse("The root shouldn't be auto-added because it is not the only one", vcsManager.hasAnyMappings())
    assertSuccessfulNotification("Unregistered VCS root detected","""
      The directory ${toSystemDependentName(projectPath)} is under mock, but is not registered in the Settings.
      <a>Add root</a> <a>Configure</a> <a>Ignore</a>
      """.trimIndent())
  }

  private fun createNestedRoots(): File {
    assertTrue(File(projectPath, DOT_MOCK).mkdir())
    val subRoot = File(projectPath, "lib")
    assertTrue(File(subRoot, DOT_MOCK).mkdirs())
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(subRoot)
    return subRoot
  }

  private fun getExtensionPoint() = Extensions.getRootArea().getExtensionPoint(VcsRootChecker.EXTENSION_POINT_NAME)

  private class MockRootChecker(private val vcs: MockAbstractVcs) : VcsRootChecker() {
    override fun getSupportedVcs() = vcs.keyInstanceMethod!!

    override fun isRoot(path: String) = File(path, DOT_MOCK).exists()

    override fun isVcsDir(path: String) = path.toLowerCase().endsWith(DOT_MOCK)
  }

}