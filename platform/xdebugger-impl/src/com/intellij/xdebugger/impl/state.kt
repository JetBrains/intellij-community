// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.xdebugger.impl.pinned.items.PinnedItemInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("breakpoint-manager")
class BreakpointManagerState : BaseState() {
  @get:XCollection(propertyElementName = "default-breakpoints")
  val defaultBreakpoints by list<BreakpointState<*, *, *>>()

  @get:XCollection(elementTypes = [BreakpointState::class, LineBreakpointState::class], style = XCollection.Style.v2)
  val breakpoints by list<BreakpointState<*, *, *>>()

  @get:XCollection(propertyElementName = "breakpoints-defaults", elementTypes = [BreakpointState::class, LineBreakpointState::class])
  val breakpointsDefaults by list<BreakpointState<*, *, *>>()

  @get:Tag("breakpoints-dialog")
  var breakpointsDialogProperties by property<XBreakpointsDialogState>()

  var defaultGroup by string()
}

@Tag("watches-manager")
internal class WatchesManagerState : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:XCollection
  val expressions by list<ConfigurationState>()

  @get:Property(surroundWithTag = false)
  @get:XCollection
  val inlineExpressionStates by list<InlineWatchState>()
}

@Tag("configuration")
internal class ConfigurationState @JvmOverloads constructor(name: String? = null,
                                                   expressions: List<XExpression>? = null) : BaseState() {
  @get:Attribute
  var name by string()

  @Suppress("MemberVisibilityCanPrivate")
  @get:Property(surroundWithTag = false)
  @get:XCollection
  val expressionStates by list<WatchState>()

  init {
    // passed values are not default - constructor provided only for convenience
    if (name != null) {
      this.name = name
    }
    if (expressions != null) {
      expressionStates.clear()
      expressions.mapTo(expressionStates) { WatchState(it) }
    }
  }
}

@Tag("inline-watch")
internal class InlineWatchState @JvmOverloads  constructor(expression: XExpression? = null, line: Int = -1, fileUrl: String? = null) : BaseState() {

  @get:Attribute
  var fileUrl by string()
  @get:Attribute
  var line by property(-1)
  @get:Property(surroundWithTag = false)
  var watchState by property<WatchState?>(null) {it == null}

  init {
    this.fileUrl = fileUrl
    this.line = line
    this.watchState = expression?.let { WatchState(it) }
  }
}

@ApiStatus.Internal
@Tag("watch")
class WatchState : XExpressionState {
  constructor() : super()

  constructor(expression: XExpression) : super(expression)
}

@ApiStatus.Internal
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