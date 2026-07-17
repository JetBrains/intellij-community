// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.HierarchyEvent
import javax.swing.JComponent

@TestApplication
internal class ComponentVisibilityTrackerTest {
  @Test
  fun `runs action whenever component becomes hidden`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val component = TestComponent()
      val tracker = ComponentVisibilityTracker(component)
      var hiddenCount = 0
      tracker.runWhenHidden(disposable) { hiddenCount++ }

      assertThat(tracker.isShowing()).isFalse()

      component.setShowing(true)
      UIUtil.dispatchAllInvocationEvents()
      assertThat(tracker.isShowing()).isTrue()
      assertThat(hiddenCount).isZero()

      component.setShowing(false)
      UIUtil.dispatchAllInvocationEvents()
      assertThat(tracker.isShowing()).isFalse()
      assertThat(hiddenCount).isEqualTo(1)

      component.setShowing(true)
      UIUtil.dispatchAllInvocationEvents()
      component.setShowing(false)
      UIUtil.dispatchAllInvocationEvents()
      assertThat(hiddenCount).isEqualTo(2)
    }
  }

  private class TestComponent : JComponent() {
    private var showing = false

    override fun isShowing(): Boolean = showing

    fun setShowing(value: Boolean) {
      showing = value
      dispatchEvent(
        HierarchyEvent(
          this,
          HierarchyEvent.HIERARCHY_CHANGED,
          this,
          parent,
          HierarchyEvent.SHOWING_CHANGED.toLong(),
        ),
      )
    }
  }
}
