// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.PathUtil
import com.intellij.util.messages.SimpleMessageBusConnection
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.nio.file.Path

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
  internal val busConnection = RecentProjectManagerListenerRule()

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

    g1.addProject(p1.toString())
    g1.addProject(p2.toString())
    g2.addProject(p3.toString())

    checkGroups(listOf("g2", "g1"))

    doReopenCloseAndCheckGroups(p4, listOf("g2", "g1"))
    doReopenCloseAndCheckGroups(p1, listOf("g1", "g2"))
    doReopenCloseAndCheckGroups(p3, listOf("g2", "g1"))
  }

  @Test
  fun timestampForOpenProjectUpdatesWhenGetStateCalled() {
    val path = tempDir.newPath("z1")
    var project = PlatformTestUtil.loadAndOpenProject(path)
    try {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      project = PlatformTestUtil.loadAndOpenProject(path)
      val timestamp = getProjectOpenTimestamp("z1")
      RecentProjectsManagerBase.instanceEx.updateLastProjectPath()
      // "Timestamp for opened project has not been updated"
      assertThat(getProjectOpenTimestamp("z1")).isGreaterThan(timestamp)
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  private fun getProjectOpenTimestamp(@Suppress("SameParameterValue") projectName: String): Long {
    val additionalInfo = RecentProjectsManagerBase.instanceEx.state.additionalInfo
    for (s in additionalInfo.keys) {
      if (s.endsWith(projectName) || s.substringBeforeLast('_').endsWith(projectName)) {
        return additionalInfo.get(s)!!.projectOpenTimestamp
      }
    }
    return -1
  }

  private fun doReopenCloseAndCheck(projectPath: Path, vararg results: String) {
    val project = PlatformTestUtil.loadAndOpenProject(projectPath)
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    checkRecents(*results)
  }

  private fun doReopenCloseAndCheckGroups(projectPath: Path, results: List<String>) {
    val project = PlatformTestUtil.loadAndOpenProject(projectPath)
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    checkGroups(results)
  }

  private fun checkRecents(vararg recents: String) {
    val recentProjects = listOf(*recents)
    val state = (RecentProjectsManager.getInstance() as RecentProjectsManagerBase).state
    val projects = state.additionalInfo.keys.asSequence()
      .map { s -> PathUtil.getFileName(s).substringAfter('_').substringBeforeLast('_') }
      .filter { recentProjects.contains(it) }
      .toList()
    assertThat(projects.reversed()).isEqualTo(recentProjects)
  }

  private fun checkGroups(groups: List<String>) {
    val recentGroups = RecentProjectListActionProvider.getInstance().getActions(false, true).asSequence()
      .filter { a -> a is ProjectGroupActionGroup }
      .map { a -> (a as ProjectGroupActionGroup).group.name }
      .toList()
    assertThat(recentGroups).isEqualTo(groups)
  }

  private fun createAndOpenProject(name: String): Path {
    val path = tempDir.newPath(name)
    var project = PlatformTestUtil.loadAndOpenProject(path)
    try {
      project.stateStore.saveComponent(RecentProjectsManager.getInstance() as RecentProjectsManagerBase)
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      project = PlatformTestUtil.loadAndOpenProject(path)
      return path
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }
}

internal class RecentProjectManagerListenerRule : ExternalResource() {
  private var connection: SimpleMessageBusConnection? = null

  override fun before() {
    connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection!!.subscribe(ProjectManager.TOPIC, RecentProjectsManagerBase.MyProjectListener())
    connection!!.subscribe(AppLifecycleListener.TOPIC, RecentProjectsManagerBase.MyAppLifecycleListener())
  }

  override fun after() {
    connection?.disconnect()
  }
}