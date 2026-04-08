// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane

class OneShotMergeFlowDelegateTest : BasePlatformTestCase() {
  fun testResolveActionButtonsUseDirectMergeContextInProviderOrder() {
    val firstAction = TestResolveAction("First")
    val secondAction = TestResolveAction("Second")
    maskProviders(
      TestProvider(order = 10, action = secondAction),
      TestProvider(order = -1, action = firstAction),
    )

    val file = myFixture.tempDirFixture.createFile("conflicts/sample.txt", "text")
    val mergeDialogCustomizer = MergeDialogCustomizer()
    var closeRequested = false
    val rootPane = JRootPane()
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      tableComponent = JPanel(),
      mergeDialogCustomizer = mergeDialogCustomizer,
      rootPane = rootPane,
      files = listOf(file),
      onClose = { closeRequested = true },
      acceptForResolution = {},
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      getGroupByDirectory = { false },
    )

    val panel = delegate.createCenterPanel()
    val buttons = UIUtil.findComponentsOfType(panel, JButton::class.java).filter { it.text == "First" || it.text == "Second" }
    assertEquals(listOf("First", "Second"), buttons.map(JButton::getText))
    buttons.first().doClick()

    val context = firstAction.performedContext
    assertNotNull(context)
    assertSame(project, context!!.project)
    assertEquals(listOf(file), context.files)
    assertEquals(rootPane.isDisplayable, context.isLaunchContextValid())
    assertFalse(closeRequested)
    context.closeDialogForAgentHandoff()
    assertTrue(closeRequested)
  }

  fun testDisabledResolveActionButtonStaysVisible() {
    maskProviders(TestProvider(action = TestResolveAction("Resolve with Agent", enabled = false)))

    val file = myFixture.tempDirFixture.createFile("conflicts/sample.txt", "text")
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      tableComponent = JPanel(),
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

  fun testResolveActionCustomComponentIsRenderedAndClickable() {
    val customAction = TestCustomComponentResolveAction("Resolve with Agent")
    maskProviders(TestProvider(action = customAction))

    val file = myFixture.tempDirFixture.createFile("conflicts/sample.txt", "text")
    val delegate = OneShotMergeFlowDelegate(
      project = project,
      tableComponent = JPanel(),
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
    button.doClick()

    assertTrue(customAction.performed)
  }

  private fun maskProviders(vararg providers: MergeResolveActionProvider) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), testRootDisposable)
  }

  private class TestProvider(
    override val action: DumbAwareAction,
    override val order: Int = 0,
  ) : MergeResolveActionProvider

  private class TestResolveAction(
    text: String,
    private val enabled: Boolean = true,
  ) : DumbAwareAction(text) {
    var performedContext: MergeResolveWithAgentContext? = null
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = true
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      performedContext = e.getData(MergeResolveWithAgentContext.KEY)
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
      e.presentation.isVisible = true
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
