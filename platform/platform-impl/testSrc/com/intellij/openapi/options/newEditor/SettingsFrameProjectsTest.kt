// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.FrameProjectConfigurable
import com.intellij.openapi.options.ex.FrameProjectsConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val APP_PAGE_ID = "settingsFrameProjectsTest.app"
private const val PROJECT_PAGE_ID = "settingsFrameProjectsTest.project"

/**
 * The Settings window of a frame that shows several projects, see [SettingsFrameProjects] and [FrameProjectsConfigurableGroup].
 * A unit test has no frame, so the record of the test window manager makes `guest` a frame guest of `owner`.
 * The test masks the settings pages of the application and of both projects, so each tree holds only the test pages.
 */
@TestApplication
@Timeout(60)
internal class SettingsFrameProjectsTest {
  // the selector lists only an open project: a frame guest that still opens is not initialized
  private val owner by projectFixture(openAfterCreation = true)
  private val guest by projectFixture(openAfterCreation = true)

  @Test
  fun `the selector lists the frame owner and its frame guests`(): Unit = inFrame {
    assertThat(SettingsFrameProjects.create(owner)!!.projects).containsExactly(owner, guest)
  }

  @Test
  fun `a frame without a frame guest has no selector`(): Unit = inFrame {
    WindowManagerEx.getInstanceEx().removeFrameGuest(guest)

    assertThat(SettingsFrameProjects.create(owner)).isNull()
  }

  @Test
  fun `a frame owner without settings of its own is not in the selector`(): Unit = inFrame { disposable ->
    suppressSettingsOfOwner(disposable)

    assertThat(SettingsFrameProjects.create(owner)!!.projects).containsExactly(guest)
  }

  @Test
  fun `the frame owner stores the selection for the next window`(): Unit = inFrame {
    SettingsFrameProjects.create(owner)!!.select(guest)

    assertThat(SettingsFrameProjects.create(owner)!!.selected).isSameAs(guest)
  }

  @Test
  fun `the tree holds an application-level page once and one page for the project-level pages of all the projects`(): Unit = inFrame { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val group = createGroup(projects, disposable)

    assertThat(SettingsFrameProjects.of(listOf(group))).isSameAs(projects)
    assertThat(findPages(group, APP_PAGE_ID)).singleElement().isInstanceOf(ConfigurableWrapper::class.java)
    assertThat(findPages(group, PROJECT_PAGE_ID)).singleElement().isInstanceOf(FrameProjectConfigurable::class.java)
  }

  @Test
  fun `a project-level page keeps the change of a project while it shows another project`(): Unit = inFrame { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val page = findPages(createGroup(projects, disposable), PROJECT_PAGE_ID).single() as FrameProjectConfigurable

    // as the Settings editor does: the component, then a reset
    assertThat(ConfigurableCardPanel.createConfigurableComponent(page)).isNotNull()
    assertThat(pageOf(owner).resets).describedAs("The first page is reset once").isEqualTo(1)
    pageOf(owner).modified = true

    projects.select(guest)
    page.showSelectedProject()

    assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isSameAs(pageOf(guest))
    assertThat(pageOf(guest).resets).isEqualTo(1)
    assertThat(page.isModified).describedAs("The change of the frame owner waits for Apply").isTrue()

    page.apply()

    assertThat(pageOf(owner).applies).isEqualTo(1)
    assertThat(pageOf(guest).applies).describedAs("Only a modified page is applied").isZero()
    assertThat(page.isModified).isFalse()
    page.disposeUIResources()
  }

  @Test
  fun `a project without the page of another project gets an empty page`(): Unit = inFrame(projectsWithPage = { listOf(guest) }) { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val page = findPages(createGroup(projects, disposable), PROJECT_PAGE_ID).single() as FrameProjectConfigurable

    val component = ConfigurableCardPanel.createConfigurableComponent(page)!!
    assertThat(UIUtil.findComponentOfType(component, JBPanelWithEmptyText::class.java)).isNotNull()
    assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isNull()

    projects.select(guest)
    page.showSelectedProject()

    assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isSameAs(pageOf(guest))
    assertThat(UIUtil.findComponentOfType(component, JBPanelWithEmptyText::class.java)).isNull()
    page.disposeUIResources()
  }

  @Test
  fun `a project that closes leaves the selector and takes its pages with it`(): Unit = inFrame { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val group = createGroup(projects, disposable)
    val page = findPages(group, PROJECT_PAGE_ID).single() as FrameProjectConfigurable
    projects.select(guest)
    assertThat(ConfigurableCardPanel.createConfigurableComponent(page)).isNotNull()
    pageOf(guest).modified = true

    group.forget(guest)
    projects.remove(guest)
    page.showSelectedProject()

    assertThat(projects.projects).containsExactly(owner)
    assertThat(projects.selected).isSameAs(owner)
    assertThat(page.isModified).describedAs("The change of the closed project is gone").isFalse()
    assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isSameAs(pageOf(owner))
    page.disposeUIResources()
  }

  @Test
  fun `the first page is reset once outside the creation of its component and keeps its decoration`(): Unit = inFrame { disposable ->
    val page = findPages(createGroup(SettingsFrameProjects.create(owner)!!, disposable), PROJECT_PAGE_ID).single() as FrameProjectConfigurable

    val component = ConfigurableCardPanel.createConfigurableComponent(page)!!

    assertThat(pageOf(owner).resets).isEqualTo(1)
    assertThat(pageOf(owner).resetsInsideCreate).describedAs("The reset runs outside the read action of the creation").isZero()
    assertThat(UIUtil.findComponentOfType(component, JScrollPane::class.java)).describedAs("The page keeps its scroll pane").isNotNull()
    page.disposeUIResources()
  }

  @Test
  fun `a page that shows again is not reset again`(): Unit = inFrame { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val page = findPages(createGroup(projects, disposable), PROJECT_PAGE_ID).single() as FrameProjectConfigurable
    assertThat(ConfigurableCardPanel.createConfigurableComponent(page)).isNotNull()

    for (project in listOf(guest, owner, guest)) {
      projects.select(project)
      page.showSelectedProject()
    }

    assertThat(pageOf(owner).resets).isEqualTo(1)
    assertThat(pageOf(guest).resets).isEqualTo(1)
    page.disposeUIResources()
  }

  @Test
  fun `the editor shows the selector only above a project-level page`(): Unit = inFrame { disposable ->
    // the tree selects a page only while it shows, and a headless test has no window
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val appPage = findPages(group, APP_PAGE_ID).single()
      val projectPage = findPages(group, PROJECT_PAGE_ID).single()
      val editor = SettingsEditor(
        disposable, owner, listOf(group), appPage, null, true,
        ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
        spotlightPainterFactory,
        null,
        false,
      )
      markAsShowing(editor)
      val tabs = UIUtil.findComponentOfType(editor.content, SingleHeightTabs::class.java)!!

      waitFor("the application-level page") { editor.selectedConfigurable === appPage }
      assertThat(isVisibleIn(editor.content, tabs)).isFalse()

      editor.treeView.select(projectPage)
      waitFor("the selector above the project-level page") { isVisibleIn(editor.content, tabs) }
      assertThat(tabs.tabs.map { it.text }).containsExactly(owner.name, guest.name)
      assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, projectPage)).isSameAs(pageOf(owner))

      tabs.select(tabs.tabs[1], false)
      waitFor("the selection of the tab") { projects.selected === guest }
      assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, projectPage)).isSameAs(pageOf(guest))
      assertThat(pageOf(guest).resets).describedAs("The editor shows the page of the selected project").isEqualTo(1)

      editor.treeView.select(appPage)
      waitFor("no selector above the application-level page") { !isVisibleIn(editor.content, tabs) }
    }
  }

  /**
   * Runs [block] on the EDT with `guest` as a frame guest of `owner`. Each project of [projectsWithPage] has the project-level
   * test page. The context is `Dispatchers.EDT`, because a settings page is created under the write-intent lock.
   */
  private fun inFrame(projectsWithPage: () -> List<Project> = { listOf(owner, guest) }, block: suspend (Disposable) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val disposable = Disposer.newDisposable()
      val windowManager = WindowManagerEx.getInstanceEx()
      windowManager.addFrameGuest(owner, guest)
      try {
        maskPages(projectsWithPage(), disposable)
        block(disposable)
      }
      finally {
        Disposer.dispose(disposable)
        windowManager.removeFrameGuest(guest)
        pagesByProject.clear()
        PropertiesComponent.getInstance(owner).unsetValue("settings.editor.selected.project")
      }
    }
  }

  private fun createGroup(projects: SettingsFrameProjects, disposable: Disposable): FrameProjectsConfigurableGroup {
    return FrameProjectsConfigurableGroup.create(projects).also { Disposer.register(disposable, it) }
  }

  private fun findPages(group: FrameProjectsConfigurableGroup, id: String): List<Configurable> {
    return ConfigurableVisitor.findAll({ ConfigurableVisitor.getId(it) == id }, listOf(group))
  }

  private fun maskPages(projectsWithPage: List<Project>, disposable: Disposable) {
    val descriptor = DefaultPluginDescriptor(
      PluginId.getId("com.intellij.openapi.options.settingsFrameProjectsTest"),
      SettingsFrameProjectsTest::class.java.classLoader,
    )
    val appPage = ConfigurableEP<Configurable>(descriptor).apply {
      id = APP_PAGE_ID
      displayName = "Application Page"
      instanceClass = FrameProjectsTestAppPage::class.java.name
    }
    ExtensionTestUtil.maskExtensions(Configurable.APPLICATION_CONFIGURABLE, listOf(appPage), disposable)
    for (project in listOf(owner, guest)) {
      val projectPage = ConfigurableEP<Configurable>(project).apply {
        pluginDescriptor = descriptor
        id = PROJECT_PAGE_ID
        displayName = "Project Page"
        instanceClass = FrameProjectsTestProjectPage::class.java.name
      }
      val point = Configurable.PROJECT_CONFIGURABLE.getPoint(project) as ExtensionPointImpl<ConfigurableEP<Configurable>>
      point.maskAll(if (project in projectsWithPage) listOf(projectPage) else emptyList(), disposable, false)
    }
  }

  /** Makes `owner` a frame owner without project-level settings of its own, as the host of a JetBrains Light workspace. */
  private fun suppressSettingsOfOwner(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(object : ProjectFrameCapabilitiesProvider {
        override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
          return if (project === owner) setOf(ProjectFrameCapability.SUPPRESS_OWN_PROJECT_SETTINGS) else emptySet()
        }

        override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
      }),
      disposable,
    )
  }

  private val spotlightPainterFactory = object : SpotlightPainterFactory {
    override fun createSpotlightPainter(
      project: Project, target: JComponent, parent: Disposable, updater: (SpotlightPainter) -> Unit,
    ): SpotlightPainter = SpotlightPainter(target, updater)
  }
}

private class FrameProjectsTestAppPage : Configurable {
  override fun getDisplayName(): String = "Application Page"

  override fun createComponent(): JComponent = JPanel()

  override fun isModified(): Boolean = false

  override fun apply() = Unit
}

/** The project-level test page. The test finds the page of a project with [pageOf]. */
private class FrameProjectsTestProjectPage(project: Project) : Configurable {
  var modified = false
  var applies = 0
  var resets = 0

  /** The resets that ran while the page of several projects created its component, so inside the read action of the creation. */
  var resetsInsideCreate = 0

  init {
    pagesByProject.put(project, this)
  }

  override fun getDisplayName(): String = "Project Page"

  override fun createComponent(): JComponent = JPanel()

  override fun isModified(): Boolean = modified

  override fun apply() {
    applies++
    modified = false
  }

  override fun reset() {
    resets++
    if (Thread.currentThread().stackTrace.any { it.className == FrameProjectConfigurable::class.java.name && it.methodName == "createComponent" }) {
      resetsInsideCreate++
    }
  }
}

/** The last project-level test page that each project created. */
private val pagesByProject = HashMap<Project, FrameProjectsTestProjectPage>()

private fun pageOf(project: Project): FrameProjectsTestProjectPage = pagesByProject.getValue(project)

/** `true` when [component] and each of its parents up to [root] are visible. */
private fun isVisibleIn(root: Component, component: Component): Boolean {
  return generateSequence(component) { if (it === root) null else it.parent }.all { it.isVisible }
}

/** Puts [component] into a container that counts as showing, as a window would. */
private fun markAsShowing(component: JComponent) {
  val container = JPanel()
  ComponentUtil.forceMarkAsShowing(container, true)
  withShowingChanged { container.add(component) }
}

private suspend fun waitFor(what: String, condition: () -> Boolean) {
  withTimeoutOrNull(5.seconds) {
    while (!condition()) {
      delay(10.milliseconds)
    }
  }
  assertThat(condition()).describedAs(what).isTrue()
}
