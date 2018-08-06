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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsUtil
import org.picocontainer.MutablePicoContainer
import java.io.File

class VcsIntegrationEnablerTest : VcsRootBaseTest() {

  private var myTestRoot: VirtualFile? = null

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    val picoContainer = myProject.picoContainer as MutablePicoContainer
    val vcsNotifierKey = VcsNotifier::class.java.name
    picoContainer.unregisterComponent(vcsNotifierKey)
    picoContainer.registerComponentImplementation(vcsNotifierKey, TestVcsNotifier::class.java)
    myTestRoot = projectRoot.parent
  }

  fun testOneRootForTheWholeProjectThenJustAddVcsRoot() {
    doTest(given("."), null, null)
  }

  fun testNoMockRootsThenInitAndNotify() {
    doTest(given(),
           notification("Created mock repository in " + projectRoot.presentableUrl), ".", VcsTestUtil.toAbsolute(".", myProject))
  }

  fun testBelowMockNoInsideThenNotify() {
    doTest(given(".."),
           notification("Added mock root: " + myTestRoot!!.presentableUrl))
  }

  fun testMockForProjectSomeInsideThenNotify() {
    doTest(given(".", "community"),
           notification("Added mock roots: " + projectRoot.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  fun testBelowMockSomeInsideThenNotify() {
    doTest(given("..", "community"),
           notification("Added mock roots: " + myTestRoot!!.presentableUrl + ", " + getPresentationForRoot("community")))
  }

  fun testNotUnderMockSomeInsideThenNotify() {
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

    val vcsRootsList = ContainerUtil.newArrayList(*vcs_roots)
    //default
    if (vcsRootsList.isEmpty()) {
      vcsRootsList.addAll(ContainerUtil.map(vcsRoots) { root ->
        assert(root.path != null)
        root.path!!.path
      })
    }
    TestIntegrationEnabler(myVcs).enable(vcsRoots)
    assertVcsRoots(vcsRootsList)
    if (mock_init != null) {
      assertMockInit(mock_init)
    }
    VcsTestUtil.assertNotificationShown(myProject, notification)
  }

  internal fun assertMockInit(root: String) {
    val rootFile = File(projectRoot.path, root)
    assertTrue(File(rootFile.path, DOT_MOCK).exists())
  }

  internal fun assertVcsRoots(expectedVcsRoots: Collection<String>) {
    val actualRoots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcsWithoutFiltering(myVcs)
    VcsTestUtil.assertEqualCollections(expectedVcsRoots, actualRoots.map { it.path })
  }

  private fun given(vararg roots: String): Collection<VcsRoot> {
    return ContainerUtil.map(roots) { s ->
      val path = VcsTestUtil.toAbsolute(s, myProject)
      LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
      VcsRoot(myVcs, VcsUtil.getVirtualFile(path))
    }
  }

  internal fun notification(content: String): Notification {
    return Notification("Test", "", content, NotificationType.INFORMATION)
  }

  private fun getPresentationForRoot(root: String): String {
    return FileUtil.toSystemDependentName(VcsTestUtil.toAbsolute(root, myProject))
  }

  private class TestIntegrationEnabler(vcs: MockAbstractVcs) : VcsIntegrationEnabler(vcs) {

    override fun initOrNotifyError(projectDir: VirtualFile): Boolean {
      val file = File(projectDir.path, DOT_MOCK)
      VcsNotifier.getInstance(myProject).notifySuccess("Created mock repository in " + projectDir.presentableUrl)
      return file.mkdir()
    }
  }
}
