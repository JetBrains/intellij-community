// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.navigation.History
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.jetbrains.concurrency.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
@Timeout(30)
internal class SettingsEditorDetachedTest {
  private val project by projectFixture()
  private val configurable by lazy { createWrappedConfigurable() }

  @Test
  fun `a detached editor gives the tree to the sidebar and the page to the content`(): Unit = withEditor(detached = true) { editor, _ ->
    assertThat(editor.componentCount).isZero()
    assertThat(SwingUtilities.isDescendingFrom(editor.treeView, editor.sidebar)).isTrue()
    assertThat(SwingUtilities.isDescendingFrom(editor.editor, editor.content)).isTrue()
    assertThat(SwingUtilities.isDescendingFrom(editor.editor, editor.sidebar)).isFalse()
  }

  @Test
  fun `the content of a detached editor gives the settings data to the page`(): Unit = withEditor(detached = true) { editor, disposable ->
    HeadlessDataManager.fallbackToProductionDataManager(disposable)

    val dataContext = DataManager.getInstance().getDataContext(editor.editor)
    assertThat(dataContext.getData(Settings.KEY)).isNotNull()
    assertThat(dataContext.getData(History.KEY)).isNotNull()
  }

  @Test
  fun `an attached editor shows the sidebar and the content itself`(): Unit = withEditor(detached = false) { editor, _ ->
    assertThat(SwingUtilities.isDescendingFrom(editor.sidebar, editor)).isTrue()
    assertThat(SwingUtilities.isDescendingFrom(editor.content, editor)).isTrue()
  }

  @Test
  fun `the tool window style paints the tree with the tool window background`(): Unit = withEditor(detached = true) { editor, _ ->
    editor.treeView.useToolWindowStyle()

    assertThat(editor.treeView.tree.background).isEqualTo(JBUI.CurrentTheme.ToolWindow.background())
  }

  @Test
  fun `the page finds its focus without the focus policy of the editor tab`(): Unit = withEditor(detached = true) { editor, _ ->
    JPanel(BorderLayout()).apply {
      isFocusCycleRoot = true
      focusTraversalPolicy = EditorTabFocusTraversalPolicy(editor)
      add(editor.content)
    }
    editor.editor.select(configurable).await()

    assertThatCode { editor.editor.getPreferredFocusedComponent() }.doesNotThrowAnyException()
  }

  /** Runs [block] on the EDT, because the tree of the editor must be disposed there. */
  private fun withEditor(detached: Boolean, block: suspend (SettingsEditor, Disposable) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.UI) {
      val disposable = Disposer.newDisposable()
      try {
        block(createEditor(disposable, detached), disposable)
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  private fun createEditor(disposable: Disposable, detached: Boolean): SettingsEditor {
    val group = object : ConfigurableGroup {
      override fun getDisplayName(): String = "Group"

      override fun getConfigurables(): Array<Configurable> = arrayOf(configurable)
    }
    return SettingsEditor(
      disposable, project, listOf(group), configurable, null, true,
      ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
      testSpotlightPainterFactory,
      null,
      detached,
    )
  }

  private fun createWrappedConfigurable(): ConfigurableWrapper {
    val descriptor = DefaultPluginDescriptor(
      PluginId.getId("com.intellij.openapi.options.settingsEditorDetachedTest"),
      SettingsEditorDetachedTest::class.java.classLoader,
    )
    val ep = ConfigurableEP<Configurable>(descriptor).apply {
      id = "page"
      displayName = "Page"
      instanceClass = PageConfigurable::class.java.name
    }
    return ConfigurableWrapper.wrapConfigurable(ep) as ConfigurableWrapper
  }

  private class PageConfigurable : Configurable {
    override fun getDisplayName(): String = "Page"

    override fun createComponent(): JComponent = JPanel()

    override fun isModified(): Boolean = false

    override fun apply() = Unit
  }
}

/** Answers every focus question with the preferred focus of the editor, as the policy of an editor tab does. */
private class EditorTabFocusTraversalPolicy(private val editor: SettingsEditor) : FocusTraversalPolicy() {
  override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? = getDefaultComponent(aContainer)

  override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? = getDefaultComponent(aContainer)

  override fun getFirstComponent(aContainer: Container): Component? = getDefaultComponent(aContainer)

  override fun getLastComponent(aContainer: Container): Component? = getDefaultComponent(aContainer)

  override fun getDefaultComponent(aContainer: Container): Component? = editor.editor.getPreferredFocusedComponent()
}

private val testSpotlightPainterFactory = object : SpotlightPainterFactory {
  override fun createSpotlightPainter(
    project: Project, target: JComponent, parent: Disposable, updater: (SpotlightPainter) -> Unit,
  ): SpotlightPainter = SpotlightPainter(target, updater)
}
