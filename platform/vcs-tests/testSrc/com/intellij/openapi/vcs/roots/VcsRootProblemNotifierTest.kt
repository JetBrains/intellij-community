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

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.roots.VcsRootBaseTest.DOT_MOCK
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcsUtil.VcsUtil.getFilePath
import java.io.File

class VcsRootProblemNotifierTest : VcsPlatformTest() {

  private lateinit var checker: VcsRootChecker
  private lateinit var vcs: MockAbstractVcs
  private lateinit var notifier : VcsRootProblemNotifier

  override fun setUp() {
    super.setUp()

    vcs = MockAbstractVcs(myProject)
    checker = MockRootChecker(vcs)
    getExtensionPoint().registerExtension(checker)
    vcsManager.registerVcs(vcs)

    notifier = VcsRootProblemNotifier.getInstance(myProject)
    Registry.get("vcs.root.auto.add.nofity").setValue(true)
  }

  override fun tearDown() {
    try {
      if (wasInit { checker }) getExtensionPoint().unregisterExtension(checker)
      if (wasInit { vcs }) vcsManager.unregisterVcs(vcs)
      Registry.get("vcs.root.auto.add.nofity").resetToDefault()
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#com.intellij.openapi.vcs.roots")

  fun `test single root equal to project dir is auto-added silently`() {
    projectRoot.createRepoDir()

    notifier.rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)
  }

  fun `test single root deeply under project dir is auto-added and reported`() {
    val deepRoot = projectRoot.createDir("lib")
    deepRoot.createRepoDir()

    notifier.rescanAndNotifyIfNeeded()

    // "Root under project dir should be auto-added"
    assertSameElements(vcsManager.allVersionedRoots, deepRoot)
    assertSuccessfulNotification("mock Integration Enabled", getPath(deepRoot.path))
  }

  fun `test two roots under project dir are auto-added and reported`() {
    val subRoot = createNestedRoots()

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, projectRoot, subRoot)
    assertSuccessfulNotification("mock Integration Enabled","""
      ${getPath(projectPath)}
      ${getPath(subRoot.path)}
      """.trimIndent())
  }

  fun `test root above project dir is auto-added and reported`() {
    val aboveRoot = testRootFile
    aboveRoot.createRepoDir()

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, aboveRoot)
    assertSuccessfulNotification("mock Integration Enabled", getPath(aboveRoot.path))
  }

  fun `test root above project dir and deeply under project dir are auto-added and reported`() {
    val deepRoot = projectRoot.createDir("lib")
    deepRoot.createRepoDir()
    val aboveRoot = testRootFile
    aboveRoot.createRepoDir()

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, aboveRoot, deepRoot)
    assertSuccessfulNotification("mock Integration Enabled","""
      ${getPath(aboveRoot.path)}
      ${getPath(deepRoot.path)}
      """.trimIndent())
  }

  fun `test if project dir is a root and there is a root above project dir, the first is auto-added silently, second is ignored`() {
    testRootFile.createRepoDir()
    projectRoot.createRepoDir()

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, projectRoot)
    assertNoNotification()
  }

  // IDEA-168690
  fun `test root is not added back if explicitly removed`() {
    assertTrue(File(projectPath, DOT_MOCK).mkdir())
    vcsManager.setDirectoryMapping(projectPath, vcs.name)
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)

    val mapping = vcsManager.getDirectoryMappingFor(getFilePath(projectRoot))
    vcsManager.removeDirectoryMapping(mapping)
    VcsConfiguration.getInstance(myProject).addIgnoredUnregisteredRoots(listOf(projectPath))

    notifier.rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertFalse("The root shouldn't be auto-added after explicit removal", vcsManager.hasAnyMappings())
  }

  // IDEA-CR-18592
  fun `test single root is not added automatically if there is ignored root`() {
    val subRoot = createNestedRoots()
    VcsConfiguration.getInstance(myProject).addIgnoredUnregisteredRoots(listOf(FileUtil.toSystemIndependentName(subRoot.path)))

    notifier.rescanAndNotifyIfNeeded()

    assertFalse("The root shouldn't be auto-added because it is not the only one", vcsManager.hasAnyMappings())
    assertSuccessfulNotification("mock Repository Found", getPath(projectPath))
  }

  private fun createNestedRoots(): VirtualFile {
    projectRoot.createRepoDir()
    val subRoot = projectRoot.createDir("lib")
    subRoot.createRepoDir()
    return subRoot
  }

  private fun VirtualFile.createRepoDir() = createDir(DOT_MOCK)

  private fun VirtualFile.createDir(name: String) = runInEdtAndGet { runWriteAction { this.createChildDirectory(this, name) } }

  private fun getExtensionPoint() = Extensions.getRootArea().getExtensionPoint(VcsRootChecker.EXTENSION_POINT_NAME)

  private fun getPath(path: String) = notifier.getPresentableMapping(path)
}