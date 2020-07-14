// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.VcsRootError.Type.EXTRA_MAPPING
import com.intellij.openapi.vcs.VcsRootErrorImpl
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcsUtil.VcsUtil

class VcsRootProblemNotifierTest : VcsPlatformTest() {
  private lateinit var rootModule: Module

  private lateinit var checker: VcsRootChecker
  private lateinit var vcs: MockAbstractVcs
  private lateinit var notifier: VcsRootProblemNotifier

  override fun setUp() {
    super.setUp()

    rootModule = doCreateRealModuleIn("foo", myProject, EmptyModuleType.getInstance())

    vcs = object : MockAbstractVcs(myProject) {
      override fun allowsNestedRoots(): Boolean = true
    }
    checker = MockRootChecker(vcs)
    VcsRootChecker.EXTENSION_POINT_NAME.point.registerExtension(checker, testRootDisposable)
    vcsManager.registerVcs(vcs)

    notifier = VcsRootProblemNotifier.getInstance(myProject)
    Registry.get("vcs.root.auto.add.nofity").setValue(true)
  }

  override fun tearDown() {
    try {
      if (::vcs.isInitialized) {
        vcsManager.unregisterVcs(vcs)
      }
      Registry.get("vcs.root.auto.add.nofity").resetToDefault()
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#com.intellij.openapi.vcs.roots")

  fun `test single root equal to project dir is auto-added silently`() {
    createRepoDir(projectRoot)

    notifier.rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)
  }

  fun `test single root deeply under project dir is auto-added and reported`() {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(deepRoot)

    notifier.rescanAndNotifyIfNeeded()

    // "Root under project dir should be auto-added"
    assertSameElements(vcsManager.allVersionedRoots, deepRoot)
    assertSuccessfulNotification("mock Integration Enabled", notifier.getPresentableMapping(deepRoot.path))
  }

  fun `test two roots under project dir are auto-added and reported`() {
    val subRoot = createNestedRoots()

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, projectRoot, subRoot)
    assertSuccessfulNotification("mock Integration Enabled","""
      ${notifier.getPresentableMapping(projectPath)}
      ${notifier.getPresentableMapping(subRoot.path)}
      """.trimIndent())
  }

  fun `test root above project dir is auto-added and reported`() {
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)
    projectRoot

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, aboveRoot)
    assertSuccessfulNotification("mock Integration Enabled", notifier.getPresentableMapping(aboveRoot.path))
  }

  fun `test root above project dir and deeply under project dir are auto-added and reported`() {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    val shallowRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(shallowRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "some/deep/folder/lib")
    createRepoDir(deepRoot)
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, aboveRoot, shallowRoot, deepRoot)
    assertSuccessfulNotification("mock Integration Enabled", """
      ${notifier.getPresentableMapping(aboveRoot.path)}
      ${notifier.getPresentableMapping(shallowRoot.path)}
      ${notifier.getPresentableMapping(deepRoot.path)}
      """.trimIndent())
  }

  fun `test root deeply under project dir are not auto-added without content root`() {
    val shallowRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(shallowRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "some/deep/folder/lib")
    createRepoDir(deepRoot)
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, aboveRoot)
    assertSuccessfulNotification("mock Integration Enabled", """
      ${notifier.getPresentableMapping(aboveRoot.path)}
      """.trimIndent())
  }

  fun `test if project dir is a root and there is a root above project dir, the first is auto-added silently, second is ignored`() {
    createRepoDir(testRoot)
    createRepoDir(projectRoot)

    notifier.rescanAndNotifyIfNeeded()

    assertSameElements(vcsManager.allVersionedRoots, projectRoot)
    assertNoNotification()
  }

  // IDEA-168690
  fun `test root is not added back if explicitly removed`() {
    createRepoDir(projectRoot)
    vcsManager.setDirectoryMapping(projectNioRoot.toString(), vcs.name)
    assertSameElements(vcsManager.allVersionedRoots, projectRoot)

    val mapping = vcsManager.getDirectoryMappingFor(VcsUtil.getFilePath(projectRoot))
    vcsManager.removeDirectoryMapping(mapping!!)
    VcsConfiguration.getInstance(project).addIgnoredUnregisteredRoots(listOf(projectPath))

    notifier.rescanAndNotifyIfNeeded()

    assertNoNotification()
    assertFalse("The root shouldn't be auto-added after explicit removal", vcsManager.hasAnyMappings())
  }

  // IDEA-CR-18592
  fun `test single root is not added automatically if there is ignored root`() {
    val subRoot = createNestedRoots()
    VcsConfiguration.getInstance(project).addIgnoredUnregisteredRoots(listOf(FileUtil.toSystemIndependentName(subRoot.path)))

    notifier.rescanAndNotifyIfNeeded()

    assertFalse("The root shouldn't be auto-added because it is not the only one", vcsManager.hasAnyMappings())
    assertSuccessfulNotification("mock Repository Found", notifier.getPresentableMapping(projectPath))
  }

  fun `test invalid roots are notified even if notification is not shown for unregistered (auto-added) roots`() {
    Registry.get("vcs.root.auto.add.nofity").setValue(false)

    vcsManager.setDirectoryMapping(projectPath, vcs.name)
    notifier.rescanAndNotifyIfNeeded()

    val rootError = VcsRootErrorImpl(EXTRA_MAPPING, projectPath, vcs.keyInstanceMethod.name)
    assertErrorNotification("Invalid VCS root mapping", notifier.getInvalidRootDescriptionItem(rootError, vcs.name))
  }

  private fun createNestedRoots(): VirtualFile {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    createRepoDir(projectRoot)
    val subRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(subRoot)
    return subRoot
  }
}

private fun createRepoDir(parent: VirtualFile) = VfsTestUtil.createDir(parent, DOT_MOCK)