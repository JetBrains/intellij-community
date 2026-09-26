// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import org.junit.Assert.assertNull
import org.junit.Test

class XDebuggerSuspendedSessionActionPromoterTest {

  @Test
  fun inertWithoutDebuggerActions() {
    val promoter = XDebuggerSuspendedSessionActionPromoter()
    assertNull(promoter.promote(listOf(PlainAction()), DataContext.EMPTY_CONTEXT))
  }

  @Test
  fun inertWithoutSession() {
    val promoter = XDebuggerSuspendedSessionActionPromoter()
    assertNull(promoter.promote(listOf(PlainAction(), DebuggerAction()), DataContext.EMPTY_CONTEXT))
  }

  private class PlainAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  private class DebuggerAction : XDebuggerActionBase() {
    override fun getHandler(): DebuggerActionHandler = throw UnsupportedOperationException()
  }
}
