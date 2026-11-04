// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.testRoot
import com.intellij.vcsUtil.VcsUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class VcsIntegrationEnablerTest {
  private val fixture = vcsRootContextFixture()
  private val context: VcsRootTestContext get() = fixture.get()

  @Test
  fun `test one root for the whole project then just add vcs root`(): Unit = with(context) {
    doTest(given("."), null, null)
  }

  @Test
  fun `test no mock roots then init and notify`(): Unit = with(context) {
    doTest(given(),
           notification("Created mock repository in " + projectRoot.presentableUrl), ".", VcsTestUtil.toAbsolute(".", project))
  }

  @Test
  fun `test below mock no inside then notify`(): Unit = with(context) {
    doTest(given(".."),
           notification("Added mock root: " + testRoot.presentableUrl))
  }

  @Test
  fun `test mock for project some inside then notify`(): Unit = with(context) {
    doTest(given(".", "community"),
           notification("Added mock roots: " + projectRoot.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  @Test
  fun `test below mock some inside then notify`(): Unit = with(context) {
    doTest(given("..", "community"),
           notification("Added mock roots: " + testRoot.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  @Test
  fun `test not under mock some inside then notify`(): Unit = with(context) {
    doTest(given("community", "contrib"),
           notification(
             "Added mock roots: " + getPresentationForRoot("community") + ", " + getPresentationForRoot("contrib"))
    )
  }

  private fun VcsRootTestContext.doTest(vcsRoots: Collection<VcsRoot>,
                                        notification: Notification?

  ) {
    doTest(vcsRoots, notification, null)
  }

  private fun VcsRootTestContext.doTest(vcsRoots: Collection<VcsRoot>,
                                        notification: Notification?,
                                        mock_init: String?,
                                        vararg vcs_roots: String) {
    for (vcsRoot in vcsRoots) {
      assertThat(File(vcsRoot.path.path, DOT_MOCK).mkdirs()).isTrue()
    }

    val vcsRootsList = mutableListOf(*vcs_roots)
    //default
    if (vcsRootsList.isEmpty()) {
      vcsRootsList.addAll(vcsRoots.map { it.path.path })
    }
    TestIntegrationEnabler(vcs).enable(vcsRoots)
    assertVcsRoots(vcsRootsList)
    if (mock_init != null) {
      assertMockInit(mock_init)
    }
    VcsTestUtil.assertNotificationShown(project, notification)
  }

  private fun VcsRootTestContext.assertMockInit(root: String) {
    val rootFile = File(projectRoot.path, root)
    assertThat(File(rootFile.path, DOT_MOCK)).exists()
  }

  private fun VcsRootTestContext.assertVcsRoots(expectedVcsRoots: Collection<String>) {
    val actualRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcsWithoutFiltering(vcs)
    assertThat(actualRoots.map { it.path }).containsExactlyInAnyOrderElementsOf(expectedVcsRoots)
  }

  private fun VcsRootTestContext.given(vararg roots: String): Collection<VcsRoot> {
    return roots.map { rootString ->
      val path = VcsTestUtil.toAbsolute(rootString, project)
      StandardFileSystems.local().refreshAndFindFileByPath(path)
      VcsRoot(vcs, VcsUtil.getVirtualFile(path)!!)
    }
  }

  private fun notification(content: String): Notification {
    @Suppress("UnresolvedPluginConfigReference")
    return Notification("Test", "", content, NotificationType.INFORMATION)
  }

  private fun VcsRootTestContext.getPresentationForRoot(root: String): String {
    return FileUtil.toSystemDependentName(VcsTestUtil.toAbsolute(root, project))
  }

  private class TestIntegrationEnabler(vcs: MockAbstractVcs) : VcsIntegrationEnabler(vcs, vcs.project.guessProjectDir()!!) {

    override fun initOrNotifyError(directory: VirtualFile): Boolean {
      val file = File(directory.path, DOT_MOCK)
      VcsNotifier.getInstance(myProject).notifySuccess(null, "", "Created mock repository in " + directory.presentableUrl)
      return file.mkdir()
    }
  }
}
