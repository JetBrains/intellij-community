// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.actions.selectRequester
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
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
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
import kotlin.time.Duration
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

  /** A project that joins the frame of `owner` while the Settings window is open. */
  private val third by projectFixture(openAfterCreation = true)

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
  fun `a page shows the first project that has it while the selected project has none`(): Unit = inFrame(projectsWithPage = { listOf(guest) }) { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val page = findPages(createGroup(projects, disposable), PROJECT_PAGE_ID).single() as FrameProjectConfigurable

    assertThat(page.projectsWithPage).containsExactly(guest)
    val component = ConfigurableCardPanel.createConfigurableComponent(page)!!
    assertThat(projects.selected).describedAs("The selection stays").isSameAs(owner)
    assertThat(page.shownProject).isSameAs(guest)
    assertThat(UIUtil.findComponentOfType(component, JBPanelWithEmptyText::class.java)).isNull()
    assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isSameAs(pageOf(guest))
    page.disposeUIResources()
  }

  @Test
  fun `the selector above a page has no tab for a project without that page`(): Unit = inFrame(projectsWithPage = { listOf(guest) }) { disposable ->
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val editor = createEditor(group, page, disposable)
      val tabs = UIUtil.findComponentOfType(editor.content, SingleHeightTabs::class.java)!!

      waitFor("the selector above the project-level page") { isVisibleIn(editor.content, tabs) }
      assertThat(tabs.tabs.map { it.text }).containsExactly(guest.name)
      assertThat(tabs.selectedInfo?.`object`).isSameAs(guest)
      assertThat(projects.selected).isSameAs(owner)
    }
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
    val page = projectPage(createGroup(SettingsFrameProjects.create(owner)!!, disposable))

    val component = ConfigurableCardPanel.createConfigurableComponent(page)!!

    assertThat(pageOf(owner).resets).isEqualTo(1)
    assertThat(pageOf(owner).resetsInsideCreate).describedAs("The reset runs outside the read action of the creation").isZero()
    assertThat(UIUtil.findComponentOfType(component, JScrollPane::class.java)).describedAs("The page keeps its scroll pane").isNotNull()
    page.disposeUIResources()
  }

  @Test
  fun `a page that shows again is not reset again`(): Unit = inFrame { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val page = projectPage(createGroup(projects, disposable))
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
  fun `Apply in the question of a frame guest that closes applies only the pages of the guest`(): Unit = withGuestPage { editor, page ->
    pageOf(owner).modified = true
    pageOf(guest).modified = true

    assertThat(SettingsFrameGuestClose.canClose(editor, guest) { Messages.YES }).isTrue()

    assertThat(pageOf(guest).applies).isEqualTo(1)
    assertThat(pageOf(owner).applies).isZero()
    assertThat(page.isModified).describedAs("The change of the frame owner waits for Apply").isTrue()
  }

  @Test
  fun `Don't Save in the question of a frame guest that closes drops only the change of the guest`(): Unit = withGuestPage { editor, page ->
    pageOf(owner).modified = true
    pageOf(guest).modified = true

    assertThat(SettingsFrameGuestClose.canClose(editor, guest) { Messages.NO }).isTrue()

    assertThat(pageOf(guest).cancels).isEqualTo(1)
    assertThat(pageOf(guest).applies).isZero()
    assertThat(pageOf(owner).cancels).isZero()
    assertThat(page.isModified).describedAs("The change of the frame owner waits for Apply").isTrue()
  }

  @Test
  fun `Cancel in the question of a frame guest that closes keeps the guest open and its change`(): Unit = withGuestPage { editor, _ ->
    pageOf(guest).modified = true

    assertThat(SettingsFrameGuestClose.canClose(editor, guest) { Messages.CANCEL }).isFalse()

    assertThat(pageOf(guest).applies).isZero()
    assertThat(pageOf(guest).cancels).isZero()
    assertThat(pageOf(guest).modified).isTrue()
  }

  @Test
  fun `a frame guest without a change closes with no question`(): Unit = withGuestPage { editor, _ ->
    assertThat(SettingsFrameGuestClose.canClose(editor, guest) { error("no question is expected") }).isTrue()
  }

  @Test
  fun `the Settings tool window asks before a frame guest with a change closes`(): Unit = inFrame { disposable ->
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val manager = SettingsToolWindowManager.getInstance(owner)
      val editor = manager.createEditor(listOf(group), page, filter = null)
      // the editor of the tool window lives as long as the project, so the tree goes before the masks of the pages
      Disposer.register(disposable) { Disposer.dispose(group) }
      markAsShowing(manager.sidebar)
      markAsShowing(manager.content)
      showGuestPage(editor, projects, page)
      pageOf(guest).modified = true
      var answer = Messages.CANCEL
      TestDialogManager.setTestDialog(TestDialog { answer }, disposable)

      assertThat(ProjectManagerEx.getInstanceEx().canClose(guest)).describedAs("Cancel keeps the frame guest open").isFalse()

      answer = Messages.YES
      assertThat(ProjectManagerEx.getInstanceEx().canClose(guest)).isTrue()
      assertThat(pageOf(guest).applies).isEqualTo(1)
    }
  }

  @Test
  fun `the Settings tool window shows the merged tree and the project tabs of the frame`(): Unit = inFrame { disposable ->
    val manager = SettingsToolWindowManager.getInstance(owner)
    // the editor of the tool window lives as long as the project. Its tree goes before the masks of the pages,
    // otherwise the end of the masks builds the tree of the real pages
    Disposer.register(disposable) { manager.configurableGroups.forEach { Disposer.dispose(it as FrameProjectsConfigurableGroup) } }

    manager.createEditorInBackground()

    waitFor("the tree of the frame", 30.seconds) { manager.configurableGroups.any { it is FrameProjectsConfigurableGroup } }
    assertThat(UIUtil.findComponentOfType(manager.content, SingleHeightTabs::class.java)).isNotNull()
  }

  @Test
  fun `a new Settings window shows the frame guest that asked for it`(): Unit = inFrame { disposable ->
    // the frame owner stores its own tab as the last selection
    SettingsFrameProjects.create(owner)!!.apply {
      select(guest)
      select(owner)
    }

    val groups = FrameProjectsConfigurableGroup.buildSettingsWindowGroups(guest)
    groups.forEach { Disposer.register(disposable, it as FrameProjectsConfigurableGroup) }
    val projects = SettingsFrameProjects.of(groups)!!
    assertThat(projects.frameOwner).describedAs("The window belongs to the frame owner").isSameAs(owner)
    assertThat(projects.selected).isSameAs(owner)

    selectRequester(groups, guest)

    assertThat(projects.selected).isSameAs(guest)
  }

  @Test
  fun `an open Settings window shows the frame guest that asked for it`(): Unit = inFrame { disposable ->
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val editor = createEditor(group, page, disposable)
      waitFor("the project-level page") { editor.selectedConfigurable === page }

      selectRequester(listOf(group), guest)

      val tabs = UIUtil.findComponentOfType(editor.content, SingleHeightTabs::class.java)!!
      waitFor("the tab of the frame guest") { tabs.selectedInfo?.`object` === guest }
      waitFor("the page of the frame guest") { ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page) === pagesByProject.get(guest) }
    }
  }

  @Test
  fun `a frame guest that opens while the Settings window is open gets a tab and a page in each page of its id`(): Unit = inFrame { disposable ->
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val editor = createEditor(group, page, disposable)
      waitFor("the project-level page") { editor.selectedConfigurable === page }
      val tabs = UIUtil.findComponentOfType(editor.content, SingleHeightTabs::class.java)!!
      pageOf(owner).modified = true

      WindowManagerEx.getInstanceEx().addFrameGuest(owner, third)
      SettingsFrameGuestJoins.guestOpened(third)

      waitFor("the tab of the frame guest that joined") { tabs.tabs.map { it.text } == listOf(owner.name, guest.name, third.name) }
      assertThat(findPages(group, PROJECT_PAGE_ID)).describedAs("The tree keeps its page").containsExactly(page)
      tabs.select(tabs.tabs[2], false)
      waitFor("the page of the frame guest that joined") {
        ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)?.let { it === pagesByProject.get(third) } == true
      }
      assertThat(page.isModified).describedAs("The change of the frame owner waits for Apply").isTrue()
    }
  }

  @Test
  fun `a frame guest that joins with a page that the tree does not have adds a node`(): Unit = inFrame(projectsWithPage = { listOf(third) }) { disposable ->
    val projects = SettingsFrameProjects.create(owner)!!
    val group = createGroup(projects, disposable)
    assertThat(findPages(group, PROJECT_PAGE_ID)).isEmpty()
    WindowManagerEx.getInstanceEx().addFrameGuest(owner, third)
    var hasNewNodes = false

    SettingsFrameGuestJoins.join(group, third) { hasNewNodes = it }

    assertThat(hasNewNodes).isTrue()
    assertThat(projects.projects).containsExactly(owner, guest, third)
    assertThat(findPages(group, PROJECT_PAGE_ID)).singleElement().isInstanceOf(FrameProjectConfigurable::class.java)
    assertThat(findPages(group, APP_PAGE_ID)).describedAs("The application-level page stays one page").hasSize(1)
  }

  @Test
  fun `the last frame guest that closes leaves the frame owner selected and hides the tabs`(): Unit = inFrame { disposable ->
    suppressSettingsOfOwner(disposable)
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val editor = createEditor(group, page, disposable)
      val tabs = UIUtil.findComponentOfType(editor.content, SingleHeightTabs::class.java)!!
      waitFor("the selector above the project-level page") { isVisibleIn(editor.content, tabs) }

      group.forget(guest)
      projects.remove(guest)

      assertThat(projects.projects).isEmpty()
      assertThat(projects.selected).describedAs("No closed project stays selected").isSameAs(owner)
      waitFor("no selector without a project") { !isVisibleIn(editor.content, tabs) }
      assertThat(ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page)).isNull()
    }
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
  private fun inFrame(projectsWithPage: () -> List<Project> = { listOf(owner, guest, third) }, block: suspend (Disposable) -> Unit) {
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
        windowManager.removeFrameGuest(third)
        pagesByProject.clear()
        PropertiesComponent.getInstance(owner).unsetValue("settings.editor.selected.project")
      }
    }
  }

  /**
   * Runs [block] while an editor shows the project-level page with the page of `guest`. The page of `owner` exists too, because
   * the editor showed it first.
   */
  private fun withGuestPage(block: suspend (SettingsEditor, FrameProjectConfigurable) -> Unit) = inFrame { disposable ->
    withForcedRespectIsShowingClientProperty {
      val projects = SettingsFrameProjects.create(owner)!!
      val group = createGroup(projects, disposable)
      val page = projectPage(group)
      val editor = createEditor(group, page, disposable)
      showGuestPage(editor, projects, page)
      block(editor, page)
    }
  }

  private suspend fun showGuestPage(editor: SettingsEditor, projects: SettingsFrameProjects, page: FrameProjectConfigurable) {
    waitFor("the project-level page") { editor.selectedConfigurable === page && pagesByProject.containsKey(owner) }
    projects.select(guest)
    waitFor("the page of the frame guest") { ConfigurableWrapper.cast(FrameProjectsTestProjectPage::class.java, page) === pagesByProject.get(guest) }
  }

  /** An editor over [group] that shows [selected]. The caller runs it under `withForcedRespectIsShowingClientProperty`. */
  private fun createEditor(group: FrameProjectsConfigurableGroup, selected: Configurable, disposable: Disposable): SettingsEditor {
    val editor = SettingsEditor(
      disposable, owner, listOf(group), selected, null, true,
      ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
      spotlightPainterFactory,
      null,
      false,
    )
    markAsShowing(editor)
    return editor
  }

  private fun projectPage(group: FrameProjectsConfigurableGroup): FrameProjectConfigurable {
    return findPages(group, PROJECT_PAGE_ID).single() as FrameProjectConfigurable
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
    for (project in listOf(owner, guest, third)) {
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
  var cancels = 0

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

  override fun cancel() {
    cancels++
    modified = false
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

private suspend fun waitFor(what: String, timeout: Duration = 5.seconds, condition: () -> Boolean) {
  withTimeoutOrNull(timeout) {
    while (!condition()) {
      delay(10.milliseconds)
    }
  }
  assertThat(condition()).describedAs(what).isTrue()
}
