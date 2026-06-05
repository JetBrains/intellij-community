// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.impl.BreakpointArea
import com.intellij.openapi.editor.impl.InterLineBreakpointConfiguration
import com.intellij.openapi.editor.impl.InterLineBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.event.InputEvent

internal class XLineBreakpointVerticalPlacementTest {
  @Test
  fun testSuggestionAndRemovalRequireSameKeys_OnLine() {
    val toPlace = BreakpointArea.OnLine(1).keyModifier
    val toRemove = XLineBreakpointVerticalPlacement.ON_LINE.keyModifier
    assertEquals(
      """
        On-line placement requires different keys to place and remove breakpoints:
        - to place:  ${InputEvent.getModifiersExText(toPlace)}
        - to remove: ${InputEvent.getModifiersExText(toRemove)}
      """.trimIndent(),
      toPlace,
      toRemove
    )
  }

  @Test
  fun testSuggestionAndRemovalRequireSameKeys_InterLine() {
    val config = InterLineBreakpointConfiguration(
      AllIcons.Actions.Cancel,
      "test",
      InterLineBreakpointProperties(false),
    )
    val toPlace = BreakpointArea.InterLine(1, config).keyModifier
    val toRemove = XLineBreakpointVerticalPlacement.INTER_LINE.keyModifier
    assertEquals(
      """
        Inter-line placement requires different keys to place and remove breakpoints:
        - to place:  ${InputEvent.getModifiersExText(toPlace)}
        - to remove: ${InputEvent.getModifiersExText(toRemove)}
      """.trimIndent(),
      toPlace,
      toRemove
    )
  }
}
