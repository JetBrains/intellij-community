// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JScrollPane

@TestApplication
@Suppress("DEPRECATION")
@RunInEdt(allMethods = false)
internal class OneShotMergeFlowDelegateTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()

  @Test
  @RunMethodInEdt
  fun resolveActionButtonsUseDirectMergeContextInProviderOrder(@TestDisposable disposable: Disposable) {
    val firstAction = TestResolveAction("First")
    val secondAction = TestResolveAction("Second")
    maskProviders(disposable,
                  TestProvider(order = 10, action = secondAction),
                  TestProvider(order = -1, action = firstAction))

    val firstFile = LightVirtualFile("sample.txt", "text")
    val secondFile = LightVirtualFile("selected.txt", "text")
    val mergeDialogCustomizer = MergeDialogCustomizer()
    var closeRequested = false
    val rootPane = JRootPane()
    assertFalse(rootPane.isDisplayable)
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = JPanel(),
      mergeDialogCustomizer = mergeDialogCustomizer,
      rootPane = rootPane,
      files = listOf(firstFile, secondFile),
      onClose = { closeRequested = true },
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    delegate.onTreeChanged(listOf(secondFile),
                           processedFiles = emptyList(),
                           unmergeableFileSelected = false,
                           unacceptableFileSelected = false)
    val buttons = UIUtil.findComponentsOfType(panel, JButton::class.java).filter { it.text == "First" || it.text == "Second" }
    assertEquals(listOf("First", "Second"), buttons.map(JButton::getText))
    buttons.first().doClick()

    val context = firstAction.performedContext
    assertNotNull(context)
    assertSame(project, context!!.project)
    assertEquals(listOf(secondFile), context.selectionHintFiles)
    assertTrue(context.isContextValid())
    assertFalse(closeRequested)
    context.closeSourceUi()
    assertTrue(closeRequested)
  }

  @Test
  @RunMethodInEdt
  fun disabledResolveActionButtonStaysVisible(@TestDisposable disposable: Disposable) {
    maskProviders(disposable, TestProvider(action = TestResolveAction("Resolve with Agent", enabled = false)))

    val file = LightVirtualFile("sample.txt", "text")
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = JPanel(),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    val button = UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.text == "Resolve with Agent" }
    assertFalse(button.isEnabled)
  }

  @Test
  @RunMethodInEdt
  fun selectionDependentResolveActionAppearsAfterSelectionChanges(@TestDisposable disposable: Disposable) {
    val file = LightVirtualFile("sample.txt", "text")
    val action = TestResolveAction("Resolve Selected", visibleWhenSelectionExists = true)
    maskProviders(disposable, TestProvider(action = action))
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = JPanel(),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    assertTrue(UIUtil.findComponentsOfType(panel, JButton::class.java).none { it.text == "Resolve Selected" && it.isVisible })

    delegate.onTreeChanged(listOf(file),
                           processedFiles = emptyList(),
                           unmergeableFileSelected = false,
                           unacceptableFileSelected = false)
    val button = UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.text == "Resolve Selected" }
    assertTrue(button.isVisible)
    button.doClick()

    assertEquals(listOf(file), action.performedContext?.selectionHintFiles)
  }

  @Test
  @RunMethodInEdt
  fun dialogInitialPreferredWidthFitsLoadedDescription() {
    val file = LightVirtualFile("sample.txt", "text")
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = JPanel(),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()

    assertTrue(panel.preferredSize.width >= JBUI.scale(650))
  }

  @Test
  @RunMethodInEdt
  fun scrollPaneUsesTableComponentAsView() {
    val file = LightVirtualFile("sample.txt", "text")
    val table = JPanel()
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = table,
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    val scrollPane = UIUtil.findComponentsOfType(panel, JScrollPane::class.java).single()

    assertSame(table, scrollPane.viewport.view)
  }

  @Test
  @RunMethodInEdt
  fun resolveActionCustomComponentIsRenderedAndClickable(@TestDisposable disposable: Disposable) {
    val customAction = TestCustomComponentResolveAction("Resolve with Agent")
    maskProviders(disposable, TestProvider(action = customAction))

    val file = LightVirtualFile("sample.txt", "text")
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      table = JPanel(),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    val button = UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.text == "Resolve with Agent" }
    panel.size = panel.preferredSize
    layoutRecursively(panel)

    assertTrue(button.isVisible)
    assertTrue(button.width > 0)
    assertTrue(button.height > 0)

    button.doClick()

    assertTrue(customAction.performed)
  }

  private fun layoutRecursively(component: java.awt.Component) {
    component.doLayout()
    if (component is Container) {
      component.components.forEach(::layoutRecursively)
    }
  }

  private fun maskProviders(disposable: Disposable, vararg providers: MergeResolveActionProvider) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), disposable)
  }

  private class TestProvider(
    override val action: DumbAwareAction,
    override val order: Int = 0,
  ) : MergeResolveActionProvider

  private class TestResolveAction(
    text: String,
    private val enabled: Boolean = true,
    private val visibleWhenSelectionExists: Boolean = false,
  ) : DumbAwareAction(text) {
    var performedContext: MergeResolveActionContext? = null
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val context = e.getData(MergeResolveActionContext.KEY)
      e.presentation.isVisible = context?.isContextValid() == true &&
                                 (!visibleWhenSelectionExists || context.selectionHintFiles.isNotEmpty())
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      performedContext = e.getData(MergeResolveActionContext.KEY)
    }
  }

  private class TestCustomComponentResolveAction(
    text: String,
    private val enabled: Boolean = true,
  ) : DumbAwareAction(text), CustomComponentAction {
    var performed: Boolean = false
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = e.getData(MergeResolveActionContext.KEY)?.isContextValid() == true
      e.presentation.isEnabled = enabled
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return JButton().also { button ->
        button.addActionListener {
          performed = true
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      (component as? JButton)?.apply {
        isVisible = presentation.isVisible
        text = presentation.text
        isEnabled = presentation.isEnabled
      }
    }
  }
}
