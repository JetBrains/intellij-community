// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui

import com.intellij.execution.testframework.sm.runner.ui.TestActivity.TestsFailed
import com.intellij.execution.testframework.sm.runner.ui.TestActivity.TestsPassed
import com.intellij.history.ActivityId
import com.intellij.history.ActivityPresentationProvider
import com.intellij.ui.JBColor
import org.jetbrains.annotations.NonNls
import java.awt.Color

internal class TestActivityPresentationProvider : ActivityPresentationProvider {
  private val RED = JBColor(Color(250, 220, 220), Color(104, 67, 67))
  private val GREEN = JBColor(Color(220, 250, 220), Color(44, 66, 60))

  override val id: String get() = ID

  companion object {
    const val ID = "Tests"
  }

  override fun getColor(kind: @NonNls String): JBColor? {
    return when (kind) {
      TestsPassed.kind -> GREEN
      TestsFailed.kind -> RED
      else -> null
    }
  }
}

internal object TestActivity {
  @JvmField
  val TestsPassed: ActivityId = ActivityId(TestActivityPresentationProvider.ID, "TestsPassed")

  @JvmField
  val TestsFailed: ActivityId = ActivityId(TestActivityPresentationProvider.ID, "TestsFailed")
}