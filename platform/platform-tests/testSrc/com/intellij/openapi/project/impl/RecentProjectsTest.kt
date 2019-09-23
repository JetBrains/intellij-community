// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.PathUtil
import com.intellij.util.containers.ContainerUtil
import org.jdom.JDOMException
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.IOException

@RunsInEdt
class RecentProjectsTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()

    @ClassRule
    @JvmField
    val edtRule = EdtRule()
  }

  @Rule
  @JvmField
  val busConnection = RecentProjectManagerListenerRule()

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @Test
  fun testMostRecentOnTop() {
    val p1 = createAndOpenProject("p1")
    val p2 = createAndOpenProject("p2")
    val p3 = createAndOpenProject("p3")

    checkRecents("p3", "p2", "p1")

    doReopenCloseAndCheck(p2, "p2", "p3", "p1")
    doReopenCloseAndCheck(p1, "p1", "p2", "p3")
    doReopenCloseAndCheck(p3, "p3", "p1", "p2")
  }

  @Test
  fun testGroupsOrder() {
    val p1 = createAndOpenProject("p1")
    val p2 = createAndOpenProject("p2")
    val p3 = createAndOpenProject("p3")
    val p4 = createAndOpenProject("p4")

    val manager = RecentProjectsManager.getInstance()
    val g1 = ProjectGroup("g1")
    val g2 = ProjectGroup("g2")
    manager.addGroup(g1)
    manager.addGroup(g2)

    g1.addProject(p1)
    g1.addProject(p2)
    g2.addProject(p3)

    checkGroups(listOf("g2", "g1"))

    doReopenCloseAndCheckGroups(p4, listOf("g2", "g1"))
    doReopenCloseAndCheckGroups(p1, listOf("g1", "g2"))
    doReopenCloseAndCheckGroups(p3, listOf("g2", "g1"))
  }

  @Test
  fun testTimestampForOpenProjectUpdatesWhenGetStateCalled() {
    var project: Project? = null
    try {
      val path = tempDir.newPath("z1")
      project = HeavyPlatformTestCase.createProject(path)
      ProjectOpeningTest.closeProject(project)
      project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(path)
      val timestamp = getProjectOpenTimestamp("z1")
      RecentProjectsManagerBase.instanceEx.updateLastProjectPath()
      // "Timestamp for opened project has not been updated"
      assertThat(getProjectOpenTimestamp("z1")).isGreaterThan(timestamp)
    }
    finally {
      ProjectOpeningTest.closeProject(project)
    }
  }

  private fun getProjectOpenTimestamp(projectName: String): Long {
    val additionalInfo = RecentProjectsManagerBase.instanceEx.state!!.additionalInfo
    for (s in additionalInfo.keys) {
      if (s.endsWith(projectName)) {
        return additionalInfo.get(s)!!.projectOpenTimestamp
      }
    }
    return -1
  }

  @Throws(IOException::class, JDOMException::class)
  private fun doReopenCloseAndCheck(projectPath: String, vararg results: String) {
    val project = ProjectManager.getInstance().loadAndOpenProject(projectPath)
    ProjectOpeningTest.closeProject(project)
    checkRecents(*results)
  }

  @Throws(IOException::class, JDOMException::class)
  private fun doReopenCloseAndCheckGroups(projectPath: String, results: List<String>) {
    val project = ProjectManager.getInstance().loadAndOpenProject(projectPath)
    ProjectOpeningTest.closeProject(project)
    checkGroups(results)
  }

  private fun checkRecents(vararg recents: String) {
    val recentProjects = listOf(*recents)
    val state = (RecentProjectsManager.getInstance() as RecentProjectsManagerBase).state
    val projects = state!!.additionalInfo.keys.asSequence()
      .map { s -> PathUtil.getFileName(s).substringAfterLast("_") }
      .filter { recentProjects.contains(it) }
      .toList()
    assertThat(ContainerUtil.reverse(projects)).isEqualTo(recentProjects)
  }

  private fun checkGroups(groups: List<String>) {
    val recentGroups = RecentProjectsManager.getInstance().getRecentProjectsActions(false, true).asSequence()
      .filter { a -> a is ProjectGroupActionGroup }
      .map { a -> (a as ProjectGroupActionGroup).group.name }
      .toList()
    assertThat(recentGroups).isEqualTo(groups)
  }

  private fun createAndOpenProject(name: String): String {
    var project: Project? = null
    try {
      val path = tempDir.newPath(name)
      project = HeavyPlatformTestCase.createProject(path)
      PlatformTestUtil.saveProject(project)
      ProjectOpeningTest.closeProject(project)
      project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(path)
      return project!!.basePath!!
    }
    finally {
      ProjectOpeningTest.closeProject(project)
    }
  }
}

class RecentProjectManagerListenerRule : ExternalResource() {
  private val disposable = Disposer.newDisposable()

  override fun before() {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, RecentProjectsManagerBase.MyProjectListener())
    connection.subscribe(AppLifecycleListener.TOPIC, RecentProjectsManagerBase.MyAppLifecycleListener())
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}