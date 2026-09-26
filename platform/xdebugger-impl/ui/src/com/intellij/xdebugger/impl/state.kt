// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.impl.breakpoints.XExpressionState
import com.intellij.xdebugger.impl.pinned.items.PinnedItemInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("watches-manager")
class WatchesManagerState : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:XCollection
  val expressions by list<ConfigurationState>()

  @get:Property(surroundWithTag = false)
  @get:XCollection
  val inlineExpressionStates by list<InlineWatchState>()
}

@ApiStatus.Internal
@Tag("configuration")
class ConfigurationState @JvmOverloads constructor(name: String? = null,
                                                   watches: List<XWatch>? = null) : BaseState() {
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
    if (watches != null) {
      expressionStates.clear()
      watches.mapTo(expressionStates) { watch ->
        WatchState(watch.expression).apply {
          canBePaused = watch.canBePaused
          isPaused = watch.isPaused
        }
      }
    }
  }
}

@ApiStatus.Internal
@Tag("inline-watch")
class InlineWatchState @JvmOverloads  constructor(expression: XExpression? = null, line: Int = -1, fileUrl: String? = null) : BaseState() {

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

  @get:Attribute
  var canBePaused: Boolean = true

  @get:Attribute
  var isPaused: Boolean = false
}

@ApiStatus.Internal
@Tag("pin-to-top-manager")
class PinToTopManagerState : BaseState() {
  @get:XCollection(propertyElementName = "pinned-members")
  var pinnedMembersList by list<PinnedItemInfo>()
}