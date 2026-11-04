// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsRootError.Type.EXTRA_MAPPING
import com.intellij.openapi.vcs.VcsRootErrorImpl
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertNoNotification
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.testRoot
import com.intellij.vcsUtil.VcsUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "vcs.root.auto.add.nofity", value = "true")
class VcsRootErrorsHandlerTest {
  private val fixture = vcsRootContextFixture { project ->
    object : MockAbstractVcs(project) {
      override fun allowsNestedRoots(): Boolean = true
    }
  }
  private val context: VcsRootTestContext get() = fixture.get()

  private lateinit var notifier: VcsRootErrorsHandler

  @BeforeEach
  fun setUp(): Unit = with(context) {
    notifier = VcsRootErrorsHandler.createInstance(project)
  }

  @Test
  fun `test single root equal to project dir is auto-added silently`(): Unit = with(context) {
    createRepoDir(projectRoot)

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertNoNotification()
    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(projectRoot)
  }

  @Test
  fun `test single root deeply under project dir is auto-added and reported`(): Unit = with(context) {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(deepRoot)

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    // "Root under project dir should be auto-added"
    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(deepRoot)
    assertSuccessfulNotification("mock Integration Enabled", notifier.getPresentableMapping(deepRoot.path))
  }

  @Test
  fun `test two roots under project dir are auto-added and reported`(): Unit = with(context) {
    val subRoot = createNestedRoots()

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(projectRoot, subRoot)
    assertSuccessfulNotification("mock Integration Enabled","""
      ${notifier.getPresentableMapping(projectPath)}
      ${notifier.getPresentableMapping(subRoot.path)}
      """.trimIndent())
  }

  @Test
  fun `test root above project dir is auto-added and reported`(): Unit = with(context) {
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)
    projectRoot

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(aboveRoot)
    assertSuccessfulNotification("mock Integration Enabled", notifier.getPresentableMapping(aboveRoot.path))
  }

  @Test
  fun `test root above project dir and deeply under project dir are auto-added and reported`(): Unit = with(context) {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    val shallowRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(shallowRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "some/deep/folder/lib")
    createRepoDir(deepRoot)
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(aboveRoot, shallowRoot, deepRoot)
    assertSuccessfulNotification("mock Integration Enabled", """
      ${notifier.getPresentableMapping(aboveRoot.path)}
      ${notifier.getPresentableMapping(shallowRoot.path)}
      ${notifier.getPresentableMapping(deepRoot.path)}
      """.trimIndent())
  }

  @Test
  fun `test root deeply under project dir are not auto-added without content root`(): Unit = with(context) {
    val shallowRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(shallowRoot)
    val deepRoot = VfsTestUtil.createDir(projectRoot, "some/deep/folder/lib")
    createRepoDir(deepRoot)
    val aboveRoot = testRoot
    createRepoDir(aboveRoot)

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(aboveRoot)
    assertSuccessfulNotification("mock Integration Enabled", """
      ${notifier.getPresentableMapping(aboveRoot.path)}
      """.trimIndent())
  }

  @Test
  fun `test if project dir is a root and there is a root above project dir, the first is auto-added silently, second is ignored`(): Unit = with(context) {
    createRepoDir(testRoot)
    createRepoDir(projectRoot)

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(projectRoot)
    assertNoNotification()
  }

  // IDEA-168690
  @Test
  fun `test root is not added back if explicitly removed`(): Unit = with(context) {
    createRepoDir(projectRoot)
    vcsManager.setDirectoryMapping(projectNioRoot.toString(), vcs.name)
    assertThat(vcsManager.getAllVersionedRoots()).containsExactlyInAnyOrder(projectRoot)

    val mapping = vcsManager.getDirectoryMappingFor(VcsUtil.getFilePath(projectRoot))
    vcsManager.removeDirectoryMapping(mapping!!)
    VcsConfiguration.getInstance(project).addIgnoredUnregisteredRoots(listOf(projectPath))

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertNoNotification()
    assertThat(vcsManager.hasAnyMappings()).describedAs("The root shouldn't be auto-added after explicit removal").isFalse()
  }

  // IDEA-CR-18592
  @Test
  fun `test single root is not added automatically if there is ignored root`(): Unit = with(context) {
    val subRoot = createNestedRoots()
    VcsConfiguration.getInstance(project).addIgnoredUnregisteredRoots(listOf(FileUtil.toSystemIndependentName(subRoot.path)))

    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    assertThat(vcsManager.hasAnyMappings()).describedAs("The root shouldn't be auto-added because it is not the only one").isFalse()
    assertSuccessfulNotification("mock Repository Found", notifier.getPresentableMapping(projectPath))
  }

  @Test
  fun `test invalid roots are notified even if notification is not shown for unregistered (auto-added) roots`(@TestDisposable disposable: Disposable): Unit = with(context) {
    Registry.get("vcs.root.auto.add.nofity").setValue(false, disposable)

    vcsManager.setDirectoryMapping(projectPath, vcs.name)
    val errors = VcsRootErrorsFinder.getInstance(project).find()
    notifier.fixAndNotifyIfNeeded(errors)

    val rootError = VcsRootErrorImpl(EXTRA_MAPPING, VcsDirectoryMapping(projectPath, vcs.keyInstanceMethod.name))
    assertErrorNotification("Invalid VCS root mapping", notifier.getInvalidRootDescriptionItem(rootError, vcs.name))
  }

  private fun VcsRootTestContext.createNestedRoots(): VirtualFile {
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    createRepoDir(projectRoot)
    val subRoot = VfsTestUtil.createDir(projectRoot, "lib")
    createRepoDir(subRoot)
    return subRoot
  }
}

private fun createRepoDir(parent: VirtualFile) = VfsTestUtil.createDir(parent, DOT_MOCK)