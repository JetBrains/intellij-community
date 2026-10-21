// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

private const val FAILING_ID = "FailingUnregisterTestToolWindow"
private const val FAILURE = "the layout of a plugin failed"

/**
 * The unregister of a tool window whose UI throws while the tool window leaves the screen.
 *
 * A layout of a plugin can throw in the validate of the tool window pane. A headless test has no tool window pane,
 * because the pane needs a frame. So the stripe button of the test throws in its removal, the step before the validate.
 */
@TestApplication
internal class ToolWindowUnregisterTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a failed unregister still disposes the tool window and keeps the exception`() {
    runInEdtAndWait {
      val buttonManager = ToolWindowPaneOldButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
      val manager = createManager(buttonManager)
      val entry = manager.registerToolWindow(RegisterToolWindowTaskData(id = FAILING_ID, anchor = ToolWindowAnchor.LEFT), buttonManager)
      val button = requireNotNull(entry.stripeButton)
      entry.stripeButton = null
      entry.stripeButton = object : StripeButtonManager by button {
        override fun remove(anchor: ToolWindowAnchor, split: Boolean) {
          button.remove(anchor, split)
          throw IllegalStateException(FAILURE)
        }
      }
      var disposed = false
      Disposer.register(entry.disposable) { disposed = true }

      assertThatThrownBy { manager.doUnregisterToolWindow(FAILING_ID) }.hasMessage(FAILURE)

      assertThat(manager.getEntry(FAILING_ID)).isNull()
      assertThat(disposed).describedAs("the disposable of the unregistered tool window is disposed").isTrue()
    }
  }

  private fun createManager(buttonManager: ToolWindowButtonManager): ToolWindowManagerImpl {
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    val manager = object : ToolWindowManagerImpl(project, isNewUi = false, isEdtRequired = false, coroutineScope = coroutineScope) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = false)
    layoutManager.noStateLoaded()
    manager.setLayoutOnInit(layoutManager.getLayoutCopy())
    return manager
  }
}
