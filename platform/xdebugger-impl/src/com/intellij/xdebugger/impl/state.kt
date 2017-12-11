/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.SmartList
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.impl.breakpoints.BreakpointState
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.XExpressionState
import java.util.*

@Tag("breakpoint-manager")
class BreakpointManagerState : BaseState() {
  @get:XCollection(propertyElementName = "default-breakpoints")
  var defaultBreakpoints by storedProperty<List<BreakpointState<*, *, *>>>(SmartList())

  @get:XCollection(elementTypes = arrayOf(BreakpointState::class, LineBreakpointState::class), style = XCollection.Style.v2)
  var breakpoints by storedProperty<List<BreakpointState<*, *, *>>>(SmartList())

  @get:XCollection(propertyElementName = "breakpoints-defaults", elementTypes = arrayOf(BreakpointState::class, LineBreakpointState::class))
  var breakpointsDefaults by storedProperty<List<BreakpointState<*, *, *>>>(SmartList())

  @get:Tag("breakpoints-dialog")
  var breakpointsDialogProperties: XBreakpointsDialogState? = null

  var time by storedProperty(0L)
  var defaultGroup by string()
}

@Tag("watches-manager")
class WatchesManagerState : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:XCollection
  var expressions by storedProperty<List<ConfigurationState>>(SmartList())
}

@Tag("configuration")
class ConfigurationState : BaseState {
  @get:Attribute
  var name by string()

  @Suppress("MemberVisibilityCanPrivate")
  @get:Property(surroundWithTag = false)
  @get:XCollection
  var expressionStates by storedProperty<List<WatchState>>(SmartList())

  @Suppress("unused")
  constructor()

  constructor(name: String, expressions: List<XExpression>) {
    this.name = name
    val list = ArrayList<WatchState>(expressions.size)
    expressionStates = list
    for (i in expressions.indices) {
      list.set(i, WatchState(expressions[i]))
    }
  }
}

@Tag("watch")
class WatchState : XExpressionState {
  @Suppress("unused")
  constructor() : super()

  constructor(expression: XExpression) : super(expression)
}

internal class XDebuggerState : BaseState() {
  @get:Property(surroundWithTag = false)
  var breakpointManagerState by bean(BreakpointManagerState())

  @get:Property(surroundWithTag = false)
  var watchesManagerState by bean(WatchesManagerState())
}