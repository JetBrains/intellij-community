// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
@Timeout(30)
internal class SettingsToolWindowManagerTest {
  private val project by projectFixture()

  @Test
  fun `a change of the last second asks before the Settings tab closes`(): Unit = withPage { manager, page ->
    var questions = 0
    TestDialogManager.setTestDialog(TestDialog { questions++; Messages.NO }, asDisposable())
    // the editor checks the page only after a delay, so it does not know about this change yet
    page.modified = true

    assertThat(manager.canCloseTab()).isTrue()
    assertThat(questions).isEqualTo(1)
    assertThat(page.resets).isEqualTo(1)
  }

  @Test
  fun `Apply in the question applies the change before the Settings tab closes`(): Unit = withPage { manager, page ->
    TestDialogManager.setTestDialog(TestDialog { Messages.YES }, asDisposable())
    page.modified = true

    assertThat(manager.canCloseTab()).isTrue()
    assertThat(page.applies).isEqualTo(1)
  }

  @Test
  fun `Cancel in the question keeps the Settings tab open`(): Unit = withPage { manager, page ->
    TestDialogManager.setTestDialog(TestDialog { Messages.CANCEL }, asDisposable())
    page.modified = true

    assertThat(manager.canCloseTab()).isFalse()
    assertThat(page.applies).isZero()
    assertThat(page.resets).isZero()
  }

  @Test
  fun `the close drops the changes of the Plugins page with no question`(): Unit = withPage(PluginManagerConfigurable.ID) { manager, page ->
    TestDialogManager.setTestDialog(TestDialog { error("no question is expected for the Plugins page") }, asDisposable())
    page.modified = true

    assertThat(manager.canCloseTab()).isTrue()
    // otherwise the next Apply of any page applies the old change of the Plugins page
    assertThat(page.resets).isEqualTo(1)
  }

  @Test
  fun `the Settings tab closes without a question when no page has a change`(): Unit = withPage { manager, _ ->
    TestDialogManager.setTestDialog(TestDialog { error("no question is expected") }, asDisposable())

    assertThat(manager.canCloseTab()).isTrue()
  }

  /**
   * Runs [block] on the EDT while the Settings editor of the project shows a [TestPage] with the given [id].
   * Apply takes the write-intent lock, as a click on a Swing button allows, so the dispatcher is [Dispatchers.EDT].
   */
  private fun withPage(id: String = "test.page", block: suspend CoroutineScope.(SettingsToolWindowManager, TestPage) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val page = TestPage(id)
      val group = object : ConfigurableGroup {
        override fun getDisplayName(): String = "Group"

        override fun getConfigurables(): Array<Configurable> = arrayOf(page)
      }
      val manager = SettingsToolWindowManager.getInstance(project)
      manager.createEditor(listOf(group), configurable = page, filter = null).treeView.select(page).await()
      // the editor resets a page when it shows the page first
      page.resets = 0
      block(manager, page)
    }
  }
}

/** A page that the test changes. Apply and Reset drop the change, as a real page does. */
private class TestPage(private val id: String) : SearchableConfigurable {
  var modified: Boolean = false
  var applies: Int = 0
  var resets: Int = 0

  override fun getId(): String = id

  override fun getDisplayName(): String = "Test Page"

  override fun createComponent(): JComponent = JPanel()

  override fun isModified(): Boolean = modified

  override fun apply() {
    applies++
    modified = false
  }

  override fun reset() {
    resets++
    modified = false
  }
}
