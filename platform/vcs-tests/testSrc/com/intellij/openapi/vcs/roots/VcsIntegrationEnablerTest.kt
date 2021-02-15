// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsUtil
import java.io.File

class VcsIntegrationEnablerTest : VcsRootBaseTest() {
  private var myTestRoot: VirtualFile? = null

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()

    myProject.replaceService(VcsNotifier::class.java, TestVcsNotifier(myProject), testRootDisposable)
    myTestRoot = projectRoot.parent
  }

  fun `test one root for the whole project then just add vcs root`() {
    doTest(given("."), null, null)
  }

  fun `test no mock roots then init and notify`() {
    doTest(given(),
           notification("Created mock repository in " + projectRoot.presentableUrl), ".", VcsTestUtil.toAbsolute(".", myProject))
  }

  fun `test below mock no inside then notify`() {
    doTest(given(".."),
           notification("Added mock root: " + myTestRoot!!.presentableUrl))
  }

  fun `test mock for project some inside then notify`() {
    doTest(given(".", "community"),
           notification("Added mock roots: " + projectRoot.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  fun `test below mock some inside then notify`() {
    doTest(given("..", "community"),
           notification("Added mock roots: " + myTestRoot!!.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  fun `test not under mock some inside then notify`() {
    doTest(given("community", "contrib"),
           notification(
             "Added mock roots: " + getPresentationForRoot("community") + ", " + getPresentationForRoot("contrib"))
    )
  }

  private fun doTest(vcsRoots: Collection<VcsRoot>,
                     notification: Notification?

  ) {
    doTest(vcsRoots, notification, null)
  }

  private fun doTest(vcsRoots: Collection<VcsRoot>,
                     notification: Notification?,
                     mock_init: String?,
                     vararg vcs_roots: String) {
    for (vcsRoot in vcsRoots) {
      assertTrue(File(vcsRoot.path.path, DOT_MOCK).mkdirs())
    }

    val vcsRootsList = mutableListOf(*vcs_roots)
    //default
    if (vcsRootsList.isEmpty()) {
      vcsRootsList.addAll(ContainerUtil.map(vcsRoots) { root ->
        root.path.path
      })
    }
    TestIntegrationEnabler(vcs).enable(vcsRoots)
    assertVcsRoots(vcsRootsList)
    if (mock_init != null) {
      assertMockInit(mock_init)
    }
    VcsTestUtil.assertNotificationShown(myProject, notification)
  }

  private fun assertMockInit(root: String) {
    val rootFile = File(projectRoot.path, root)
    assertTrue(File(rootFile.path, DOT_MOCK).exists())
  }

  private fun assertVcsRoots(expectedVcsRoots: Collection<String>) {
    val actualRoots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcsWithoutFiltering(vcs)
    VcsTestUtil.assertEqualCollections(actualRoots.map { it.path }, expectedVcsRoots)
  }

  private fun given(vararg roots: String): Collection<VcsRoot> {
    return ContainerUtil.map(roots) { s ->
      val path = VcsTestUtil.toAbsolute(s, myProject)
      LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
      VcsRoot(vcs, VcsUtil.getVirtualFile(path)!!)
    }
  }

  @Suppress("UnresolvedPluginConfigReference")
  internal fun notification(content: String): Notification {
    return Notification("Test", "", content, NotificationType.INFORMATION)
  }

  private fun getPresentationForRoot(root: String): String {
    return FileUtil.toSystemDependentName(VcsTestUtil.toAbsolute(root, myProject))
  }

  private class TestIntegrationEnabler(vcs: MockAbstractVcs) : VcsIntegrationEnabler(vcs) {

    override fun initOrNotifyError(projectDir: VirtualFile): Boolean {
      val file = File(projectDir.path, DOT_MOCK)
      VcsNotifier.getInstance(myProject).notifySuccess(null, "", "Created mock repository in " + projectDir.presentableUrl)
      return file.mkdir()
    }
  }
}
