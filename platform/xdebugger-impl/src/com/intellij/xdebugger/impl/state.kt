// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.impl.breakpoints.BreakpointState
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.XExpressionState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("breakpoint-manager")
class BreakpointManagerState : BaseState() {
  @get:XCollection(propertyElementName = "default-breakpoints")
  val defaultBreakpoints by list<BreakpointState>()

  @get:XCollection(elementTypes = [BreakpointState::class, LineBreakpointState::class], style = XCollection.Style.v2)
  val breakpoints by list<BreakpointState>()

  @get:XCollection(propertyElementName = "breakpoints-defaults", elementTypes = [BreakpointState::class, LineBreakpointState::class])
  val breakpointsDefaults by list<BreakpointState>()

  @get:Tag("breakpoints-dialog")
  var breakpointsDialogProperties by property<XBreakpointsDialogState>()

  var defaultGroup by string()
}

internal class XDebuggerState : BaseState() {
  @get:Property(surroundWithTag = false)
  var breakpointManagerState by property(BreakpointManagerState())

  @get:Property(surroundWithTag = false)
  var watchesManagerState by property(WatchesManagerState())

  @get:Property(surroundWithTag = false)
  var pinToTopManagerState by property(PinToTopManagerState())
}