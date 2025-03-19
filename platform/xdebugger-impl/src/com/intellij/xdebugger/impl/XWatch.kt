// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.XExpression

/**
 * Watch entity represents an expression to be evaluated with an additional state.
 *
 * This entity is used through the whole session and can be used between sessions via serialization performed by [XDebuggerWatchesManager].
 */
sealed interface XWatch {
  val expression: XExpression
  val canBePaused: Boolean

  var isPaused: Boolean
}

/**
 * A watch whose evaluation can be paused due to improper context or side effects.
 */
internal class XWatchImpl(override val expression: XExpression) : XWatch {
  override val canBePaused: Boolean get() = true
  override var isPaused: Boolean = false
}

/**
 * A watch that does not have an ability to be paused.
 *
 * For example, evaluation result, inline watches.
 */
internal class XAlwaysEvaluatedWatch(override val expression: XExpression) : XWatch {
  override val canBePaused: Boolean get() = false
  override var isPaused: Boolean
    get() = false
    set(_) = error("isPaused is not modifiable for AlwaysEvaluatedWatch")
}
