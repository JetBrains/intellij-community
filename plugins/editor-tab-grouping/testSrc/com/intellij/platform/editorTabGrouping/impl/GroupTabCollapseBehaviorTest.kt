// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.ide.ui.UISettings
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.TabLabel.ActionsPosition
import com.intellij.ui.tabs.impl.TabListOptions
import java.awt.Dimension
import javax.swing.JLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
class GroupTabCollapseBehaviorTest {

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var tabsDisposable: Disposable
  private lateinit var project: MockProjectEx
  private lateinit var contentRoot: VirtualFile
  private var savedGroupByDirectory: Boolean = false
  private var savedShowGroupNames: Boolean = false
  private var savedCollapseGroups: Boolean = false

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      tabsDisposable = Disposer.newDisposable()
      Disposer.register(disposable, tabsDisposable)
      project = MockProjectEx(disposable)
      contentRoot = makeDir("/project")
      project.registerService(ProjectFileIndex::class.java, StubProjectFileIndex(contentRoot))
      val settings = EditorTabGroupingSettings.getInstance()
      savedGroupByDirectory = settings.groupByDirectory
      savedShowGroupNames = settings.showGroupNames
      savedCollapseGroups = settings.collapseGroups
      settings.groupByDirectory = true
      settings.showGroupNames = true
      settings.collapseGroups = true
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val settings = EditorTabGroupingSettings.getInstance()
      settings.groupByDirectory = savedGroupByDirectory
      settings.showGroupNames = savedShowGroupNames
      settings.collapseGroups = savedCollapseGroups
      Disposer.dispose(tabsDisposable)
    }
  }

  @Test
  fun `selecting a tab outside a group collapses it into one named tab`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()

      tabs.select(tabs.tabByText("beta1"), false)

      assertEquals(listOf("Alpha (3)", "beta1", "beta2"), tabs.visibleLabelTexts())
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")
      assertSame(tabs.tabByText("alpha1"), groupTab)
      assertEquals(listOf(false, true, true), listOf("alpha1", "alpha2", "alpha3").map { tabs.tabByText(it).isHidden })
    }
  }

  @Test
  fun `a collapsed group keeps each tab a file tab`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()

      tabs.select(tabs.tabByText("beta1"), false)

      // The editor window reads the file of each tab, so a collapse adds no tab and changes no tab text.
      assertEquals(5, tabs.tabCount)
      assertTrue(tabs.tabs.all { it.`object` is VirtualFile })
      assertEquals(listOf("alpha1", "beta1", "beta2"), tabs.visibleTabTexts())
    }
  }

  @Test
  fun `selecting a hidden member expands the group and keeps the selection on that member`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val alpha3 = tabs.tabByText("alpha3")
      tabs.select(tabs.tabByText("beta1"), false)

      tabs.select(alpha3, false)

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)"), tabs.visibleLabelTexts())
      assertSame(alpha3, tabs.selectedInfo)
    }
  }

  @Test
  fun `clicking the group tab expands the group and selects the last used tab`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val alpha2 = tabs.tabByText("alpha2")
      tabs.select(alpha2, false)
      tabs.select(tabs.tabByText("beta1"), false)
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!
      assertEquals(listOf("Alpha (3)", "beta1", "beta2"), tabs.visibleLabelTexts())

      tabs.select(groupTab, true)

      // Expanding one group collapses the other shown group, so only the selected group stays expanded.
      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)"), tabs.visibleLabelTexts())
      assertSame(alpha2, tabs.selectedInfo)
      assertNull(tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha"))
    }
  }

  @Test
  fun `the startup selection collapses the other groups without a tab change`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabs()
      val restored = listOf(
        tab("alpha1", "/project/Alpha/Alpha1.kt"), tab("alpha2", "/project/Alpha/Alpha2.kt"),
        tab("beta1", "/project/Beta/Beta1.kt"), tab("beta2", "/project/Beta/Beta2.kt"),
      )

      tabs.setTabsFromWindow(restored)
      // No tab has the selection yet, and the component reports its first tab. No group may collapse on that report.
      assertEquals(listOf("alpha1", "alpha2", "beta1", "beta2"), tabs.visibleLabelTexts())
      tabs.selectAtStartup(restored[2])

      assertEquals(listOf("Alpha (2)", "beta1", "beta2"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `the tab of the startup selection is the last used tab`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val alpha2 = tabs.tabByText("alpha2")
      tabs.selectAtStartup(alpha2)
      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)"), tabs.visibleLabelTexts())

      tabs.select(tabs.tabByText("beta1"), false)

      assertSame(alpha2, tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha"))
    }
  }

  @Test
  fun `expanding keeps the member order when the group tab is not the first member`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val gamma = tab("gamma", "/project/Gamma/Gamma.kt")
      tabs.addTabSilently(gamma, tabs.tabCount)
      tabs.awaitGroupResolution()
      tabs.select(tabs.tabByText("beta2"), false)
      tabs.select(tabs.tabByText("alpha3"), false)
      tabs.select(gamma, false)
      assertEquals(listOf("Alpha (3)", "Beta (2)", "gamma"), tabs.visibleLabelTexts())

      tabs.select(tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Beta")!!, false)

      assertEquals(listOf("Alpha (3)", "beta1", "beta2", "gamma"), tabs.visibleLabelTexts())
      tabs.select(tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!, false)
      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)", "gamma"), tabs.visibleLabelTexts())
      assertSame(tabs.tabByText("alpha3"), tabs.selectedInfo)
    }
  }

  @Test
  fun `the group tab has no close button and gets it back on expand`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      for (info in tabs.tabs) info.setTabLabelActions(DefaultActionGroup(), ActionPlaces.EDITOR_TAB)
      val beta1 = tabs.tabByText("beta1")
      tabs.select(beta1, false)
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!
      val shown = beta1.tabLabel!!.actionsPosition
      assertNotEquals(ActionsPosition.NONE, shown)

      assertEquals(ActionsPosition.NONE, groupTab.tabLabel!!.actionsPosition)
      // The platform sets the actions of each label again on a settings change.
      tabs.uiSettingsChanged(UISettings.getInstance())
      assertEquals(ActionsPosition.NONE, groupTab.tabLabel!!.actionsPosition)

      tabs.select(groupTab, false)
      assertEquals(shown, groupTab.tabLabel!!.actionsPosition)
    }
  }

  @Test
  fun `collapse and expand keep each tab, its label and its component`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val before = tabs.tabs.map { Triple(it, it.tabLabel, it.component) }
      var removedCount = 0
      tabs.addListener(object : TabsListener {
        override fun tabRemoved(tabToRemove: TabInfo) {
          removedCount++
        }
      })

      tabs.select(tabs.tabByText("beta1"), false)
      assertEquals(before.toSet(), tabs.tabs.map { Triple(it, it.tabLabel, it.component) }.toSet())
      // A hidden member keeps its label in the component, so an expand creates nothing.
      assertTrue(before.all { (_, label, _) -> label!!.parent === tabs })
      tabs.select(tabs.tabByText("alpha2"), false)

      assertEquals(before, tabs.tabs.map { Triple(it, it.tabLabel, it.component) })
      assertEquals(0, removedCount)
    }
  }

  @Test
  fun `the group tab keeps the group decoration`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!

      assertEquals("Alpha", tabs.groupFor(groupTab)?.name)
      assertEquals(3, tabs.logicalGroupSizes()[false to "/project/Alpha"])
      assertEquals(listOf(groupTab), tabs.visibleGroupTabs().filter { tabs.groupFor(it)?.name == "Alpha" })
    }
  }

  @Test
  fun `selecting an ungrouped tab collapses every shown group`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val gamma = tab("gamma", "/project/Gamma/Gamma.kt")
      tabs.addTabSilently(gamma, tabs.tabCount)
      tabs.awaitGroupResolution()

      tabs.select(gamma, false)

      assertEquals(listOf("Alpha (3)", "Beta (2)", "gamma"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `a colliding group name shows the first differing directory`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabs()
      val two1 = tab("two1", "/project/mod2/com/src/Two1.kt")
      tabs.addTabSilently(tab("one1", "/project/mod1/com/src/One1.kt"), tabs.tabCount)
      tabs.addTabSilently(tab("one2", "/project/mod1/com/src/One2.kt"), tabs.tabCount)
      tabs.addTabSilently(two1, tabs.tabCount)
      tabs.addTabSilently(tab("two2", "/project/mod2/com/src/Two2.kt"), tabs.tabCount)
      tabs.awaitGroupResolution()

      tabs.select(two1, false)

      assertEquals(listOf("mod1/…/src (2)", "two1", "two2"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `equal short names keep the full differing path`() {
    fun group(key: String) = TabGroup(key.substringAfterLast('/'), key)
    val groups = listOf(group("/p/x/m/src"), group("/p/y/m/src"), group("/p/x/n/src"), group("/p/y/n/src"))

    val names = collapsedGroupNames(groups.take(3), groups)

    assertEquals("x/m/src", names["/p/x/m/src"])
    assertEquals("y/…/src", names["/p/y/m/src"])
    assertEquals("x/n/src", names["/p/x/n/src"])
  }

  @Test
  fun `closing a member updates the count and below two members the group expands`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)

      tabs.removeTab(tabs.tabByText("alpha3"))
      assertEquals(listOf("Alpha (2)", "beta1", "beta2"), tabs.visibleLabelTexts())

      tabs.removeTab(tabs.tabByText("alpha2"))
      // One member is not a shown group, and the tab order puts a tab without a shown group last.
      assertEquals(listOf("beta1", "beta2", "alpha1"), tabs.visibleLabelTexts())
      assertNull(tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha"))
    }
  }

  @Test
  fun `closing the group tab makes another member the group tab`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("alpha3"), false)
      tabs.select(tabs.tabByText("alpha2"), false)
      tabs.select(tabs.tabByText("beta1"), false)
      assertSame(tabs.tabByText("alpha2"), tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha"))

      tabs.removeTab(tabs.tabByText("alpha2"))

      assertEquals(listOf("Alpha (2)", "beta1", "beta2"), tabs.visibleLabelTexts())
      assertSame(tabs.tabByText("alpha3"), tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha"))
    }
  }

  @Test
  fun `a file close expands the groups and collapses them again after it`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val beta2 = tabs.tabByText("beta2")
      tabs.select(tabs.tabByText("beta1"), false)
      val manager = tabs.editorWindow.manager
      val file = beta2.`object` as VirtualFile

      project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileClosed(manager, file)
      // The editor window computes the tab to select by index here, so no tab may be hidden.
      assertTrue(tabs.tabs.none { it.isHidden })
      tabs.removeTab(beta2)
      project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file)

      assertEquals(listOf("Alpha (3)", "beta1"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `a text change of the group tab keeps the group label and expand shows the new text`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!

      groupTab.setText("renamed")
      assertEquals(listOf("Alpha (3)", "beta1", "beta2"), tabs.visibleLabelTexts())

      tabs.select(groupTab, false)
      assertEquals(listOf("renamed", "alpha2", "alpha3", "Beta (2)"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `a tab opened inside a collapsed group joins it and selecting it expands the group`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)
      val added = tab("alpha4", "/project/Alpha/Alpha4.kt")

      tabs.addTabSilently(added, tabs.tabCount)
      tabs.awaitGroupResolution()
      assertEquals(listOf("Alpha (4)", "beta1", "beta2"), tabs.visibleLabelTexts())

      tabs.select(added, false)

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "alpha4", "Beta (2)"), tabs.visibleLabelTexts())
      assertSame(added, tabs.selectedInfo)
    }
  }

  @Test
  fun `a selected tab that resolves into a collapsed group expands the group`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)
      val added = tab("alpha4", "/project/Alpha/Alpha4.kt")

      tabs.addTabSilently(added, tabs.tabCount)
      tabs.select(added, false)
      tabs.awaitGroupResolution()

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "alpha4", "Beta (2)"), tabs.visibleLabelTexts())
      assertSame(added, tabs.selectedInfo)
    }
  }

  @Test
  fun `switching the feature off expands every collapsed group`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)
      assertEquals(listOf("Alpha (3)", "beta1", "beta2"), tabs.visibleLabelTexts())

      EditorTabGroupingSettings.getInstance().collapseGroups = false
      tabs.uiSettingsChanged(UISettings.getInstance())

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "beta1", "beta2"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `switching the feature on collapses the groups without a new selection`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      EditorTabGroupingSettings.getInstance().collapseGroups = false
      val tabs = makeTabsWithAlphaGroup()
      tabs.select(tabs.tabByText("beta1"), false)

      EditorTabGroupingSettings.getInstance().collapseGroups = true
      tabs.uiSettingsChanged(UISettings.getInstance())

      assertEquals(listOf("Alpha (3)", "beta1", "beta2"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `the feature keeps the current behavior when disabled`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      EditorTabGroupingSettings.getInstance().collapseGroups = false
      val tabs = makeTabsWithAlphaGroup()

      tabs.select(tabs.tabByText("beta1"), false)

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "beta1", "beta2"), tabs.visibleLabelTexts())
    }
  }

  @Test
  fun `a collapse animates the group tab from the group width to its own width`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      tabs.doLayout()
      val groupWidth = listOf("alpha1", "alpha2", "alpha3").sumOf { tabs.tabByText(it).tabLabel!!.width }
      assertTrue(groupWidth > 0)

      tabs.select(tabs.tabByText("beta1"), false)

      val animation = tabs.collapseAnimationForTest()
      val label = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!.tabLabel!!
      assertTrue(animation.isRunning)
      assertEquals(groupWidth, label.preferredSize.width)
      animation.finishNow()
      assertFalse(label.isPreferredSizeSet)
      assertTrue(label.preferredSize.width < groupWidth)
    }
  }

  @Test
  fun `an expand animates the members from the width of the group tab to their own widths`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val animation = tabs.collapseAnimationForTest()
      tabs.select(tabs.tabByText("beta1"), false)
      animation.finishNow()
      tabs.doLayout()
      val groupTab = tabs.collapsedGroupTabForTest(isPinned = false, groupKey = "/project/Alpha")!!
      val groupTabWidth = groupTab.tabLabel!!.width
      assertTrue(groupTabWidth > 0)

      tabs.select(groupTab, false)

      val labels = listOf("alpha1", "alpha2", "alpha3").map { tabs.tabByText(it).tabLabel!! }
      assertTrue(animation.isRunning)
      assertEquals(groupTabWidth, labels.sumOf { it.preferredSize.width })
      animation.finishNow()
      assertFalse(labels.any { it.isPreferredSizeSet })
    }
  }

  @Test
  fun `a file close has no animation`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val animation = tabs.collapseAnimationForTest()
      val beta2 = tabs.tabByText("beta2")
      tabs.select(tabs.tabByText("beta1"), false)
      animation.finishNow()
      tabs.doLayout()
      val manager = tabs.editorWindow.manager
      val file = beta2.`object` as VirtualFile

      project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileClosed(manager, file)
      tabs.removeTab(beta2)
      project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file)

      assertEquals(listOf("Alpha (3)", "beta1"), tabs.visibleLabelTexts())
      assertFalse(animation.isRunning)
    }
  }

  @Test
  fun `a file close that moves the selection out of a group collapses the group after the removal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabs()
      val gamma = tab("gamma", "/project/Gamma/Gamma.kt")
      val alpha3 = tab("alpha3", "/project/Alpha/Alpha3.kt")
      for (info in listOf(tab("alpha1", "/project/Alpha/Alpha1.kt"), tab("alpha2", "/project/Alpha/Alpha2.kt"), alpha3, gamma)) {
        tabs.addTabSilently(info, tabs.tabCount)
      }
      tabs.awaitGroupResolution()
      // The selected tab is in the one group, so no group is collapsed when the close starts.
      tabs.select(alpha3, false)
      tabs.doLayout()
      val manager = tabs.editorWindow.manager
      val file = alpha3.`object` as VirtualFile
      val hiddenStates = ArrayList<Boolean>()
      tabs.addListener(object : TabsListener {
        override fun selectionChanged(oldInfo: TabInfo?, newInfo: TabInfo?) {
          hiddenStates.add(tabs.tabs.any { it.isHidden })
        }
      })

      project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileClosed(manager, file)
      tabs.removeTab(alpha3, gamma)
      project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file)

      // The selection moves before the removal. No tab may be hidden then, and the tab of the close is no group tab.
      assertEquals(listOf(false), hiddenStates)
      assertEquals(listOf("Alpha (2)", "gamma"), tabs.visibleLabelTexts())
      assertFalse(tabs.collapseAnimationForTest().isRunning)
    }
  }

  @Test
  fun `a new tab in the selected group changes no group and starts no animation`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val animation = tabs.collapseAnimationForTest()
      tabs.select(tabs.tabByText("beta1"), false)
      tabs.select(tabs.tabByText("alpha1"), false)
      animation.finishNow()
      tabs.doLayout()
      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)"), tabs.visibleLabelTexts())
      val added = tab("alpha4", "/project/Alpha/Alpha4.kt")
      val gate = CompletableDeferred<Unit>()
      tabs.resolutionGate = gate

      // The editor window adds the tab and selects it. The group of the tab resolves later.
      tabs.addTabSilently(added, tabs.tabCount)
      tabs.select(added, false)
      assertEquals(listOf("alpha1", "alpha2", "alpha3", "Beta (2)", "alpha4"), tabs.visibleLabelTexts())
      assertFalse(animation.isRunning)
      gate.complete(Unit)
      tabs.awaitGroupResolution()

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "alpha4", "Beta (2)"), tabs.visibleLabelTexts())
      assertFalse(animation.isRunning)
    }
  }

  @Test
  fun `a new tab in a collapsed group expands the group with an animation`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val animation = tabs.collapseAnimationForTest()
      tabs.select(tabs.tabByText("beta1"), false)
      animation.finishNow()
      tabs.doLayout()
      val added = tab("alpha4", "/project/Alpha/Alpha4.kt")
      val gate = CompletableDeferred<Unit>()
      tabs.resolutionGate = gate

      tabs.addTabSilently(added, tabs.tabCount)
      tabs.select(added, false)
      gate.complete(Unit)
      tabs.awaitGroupResolution()

      assertEquals(listOf("alpha1", "alpha2", "alpha3", "alpha4", "Beta (2)"), tabs.visibleLabelTexts())
      assertTrue(animation.isRunning)
    }
  }

  @Test
  fun `a removed tab changes the groups without an animation`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val tabs = makeTabsWithAlphaGroup()
      val animation = tabs.collapseAnimationForTest()
      tabs.select(tabs.tabByText("beta1"), false)
      animation.finishNow()
      tabs.doLayout()

      tabs.removeTab(tabs.tabByText("alpha3"))
      tabs.removeTab(tabs.tabByText("alpha2"))

      assertEquals(listOf("beta1", "beta2", "alpha1"), tabs.visibleLabelTexts())
      assertFalse(animation.isRunning)
    }
  }

  @Test
  fun `findSelectionNeighbor returns the nearest enabled tab, the previous tabs first`() {
    fun info(text: String, enabled: Boolean = true): TabInfo {
      val result = TabInfo(JLabel(text)).setText(text)
      if (!enabled) result.isEnabled = false
      return result
    }
    val tabs = listOf(info("a"), info("b", enabled = false), info("c"), info("d"))
    assertEquals(tabs[0], findSelectionNeighbor(tabs, 2))
    assertEquals(tabs[2], findSelectionNeighbor(tabs, 0))
    assertNull(findSelectionNeighbor(listOf(info("off", enabled = false), info("p")), 1))
  }

  private suspend fun makeTabs(): TestGroupingJBEditorTabs {
    val scope = CoroutineScope(currentCoroutineContext())
    return TestGroupingJBEditorTabs(project, tabsDisposable, scope, TabListOptions(), true).apply {
      size = Dimension(800, 200)
    }
  }

  private suspend fun makeTabsWithAlphaGroup(): TestGroupingJBEditorTabs {
    val tabs = makeTabs().apply {
      addTabSilently(tab("alpha1", "/project/Alpha/Alpha1.kt"), tabCount)
      addTabSilently(tab("alpha2", "/project/Alpha/Alpha2.kt"), tabCount)
      addTabSilently(tab("alpha3", "/project/Alpha/Alpha3.kt"), tabCount)
      addTabSilently(tab("beta1", "/project/Beta/Beta1.kt"), tabCount)
      addTabSilently(tab("beta2", "/project/Beta/Beta2.kt"), tabCount)
    }
    tabs.awaitGroupResolution()
    return tabs
  }

  /**
   * Selects [info] as the editor window does at startup: without a selection event.
   * In the IDE the groups resolve after that selection, so the test resolves them again.
   */
  private suspend fun TestGroupingJBEditorTabs.selectAtStartup(info: TabInfo) {
    selectTabSilently(info)
    windowSelection = true
    onContentRootsChanged()
    awaitGroupResolution()
  }

  private fun tab(name: String, path: String): TabInfo =
    TabInfo(JLabel(name)).setObject(makeFile(path)).apply { setText(name) }

  private fun GroupingJBEditorTabs.tabByText(name: String): TabInfo =
    tabs.first { it.text == name }

  private fun makeDir(path: String): VirtualFile {
    val name = path.substringAfterLast('/', path)
    val parentPath = path.substringBeforeLast('/')
    val parent: VirtualFile? = if (parentPath.isNotEmpty() && parentPath != path) makeDir(parentPath) else null
    return object : LightVirtualFile(name) {
      override fun getPath(): String = path
      override fun isDirectory(): Boolean = true
      override fun getParent(): VirtualFile? = parent
    }
  }

  private fun makeFile(path: String): VirtualFile {
    val parentPath = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val parent = makeDir(parentPath)
    return object : LightVirtualFile(name) {
      override fun getPath(): String = path
      override fun isDirectory(): Boolean = false
      override fun getParent(): VirtualFile = parent
    }
  }
}
