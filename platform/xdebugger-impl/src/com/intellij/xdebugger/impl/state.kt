// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.xdebugger.impl.pinned.items.PinnedItemInfo

@Tag("breakpoint-manager")
class BreakpointManagerState : BaseState() {
  @get:XCollection(propertyElementName = "default-breakpoints")
  var defaultBreakpoints by list<BreakpointState<*, *, *>>()

  @get:XCollection(elementTypes = [BreakpointState::class, LineBreakpointState::class], style = XCollection.Style.v2)
  var breakpoints by list<BreakpointState<*, *, *>>()

  @get:XCollection(propertyElementName = "breakpoints-defaults", elementTypes = [BreakpointState::class, LineBreakpointState::class])
  var breakpointsDefaults by list<BreakpointState<*, *, *>>()

  @get:Tag("breakpoints-dialog")
  var breakpointsDialogProperties by property<XBreakpointsDialogState>()

  var defaultGroup by string()
}

@Tag("watches-manager")
class WatchesManagerState : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:XCollection
  var expressions by list<ConfigurationState>()
}

@Tag("configuration")
class ConfigurationState @JvmOverloads constructor(name: String? = null, expressions: List<XExpression>? = null) : BaseState() {
  @get:Attribute
  var name by string()

  @Suppress("MemberVisibilityCanPrivate")
  @get:Property(surroundWithTag = false)
  @get:XCollection
  var expressionStates by list<WatchState>()

  init {
    // passed values are not default - constructor provided only for convenience
    if (name != null) {
      this.name = name
    }
    if (expressions != null) {
      expressionStates = expressions.mapTo(SmartList()) { WatchState(it) }
    }
  }
}

@Tag("watch")
class WatchState : XExpressionState {
  @Suppress("unused")
  constructor() : super()

  constructor(expression: XExpression) : super(expression)
}

@Tag("pin-to-top-manager")
class PinToTopManagerState : BaseState() {
    @get:XCollection(propertyElementName = "pinned-members")
    var pinnedMembersList by list<PinnedItemInfo>()
}

internal class XDebuggerState : BaseState() {
  @get:Property(surroundWithTag = false)
  var breakpointManagerState by property(BreakpointManagerState())

  @get:Property(surroundWithTag = false)
  var watchesManagerState by property(WatchesManagerState())

  @get:Property(surroundWithTag = false)
  var pinToTopManagerState by property(PinToTopManagerState())
}