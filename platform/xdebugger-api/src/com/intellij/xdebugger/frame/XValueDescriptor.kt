// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Provides additional information about XValue which can be used by UI and actions.
 *
 */
@ApiStatus.Internal
@Serializable
data class XValueDescriptor(
  /**
   * [kind] is used to differentiate various implementations of XValue by their type instead of using `instanceOf` on [XValue].
   * Examples of possible kinds: "JavaValue", "PhpValue", "RubyValue"
   *
   * Implementation detail: since the frontend uses [com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue],
   * which makes `instanceOf` comparisons of XValue impossible, this property provides a way to
   * differentiate between various types of XValue on the frontend.
   */
  val kind: String,

  /**
   * [XValue] type (see instances of the [XValueType]).
   * Typically, the client may use the type to check the availability of some action or show XValue differently in UI.
   */
  val type: XValueType,
)

@ApiStatus.Internal
@Serializable
sealed interface XValueType {
  // TODO: add other types of XValue
  @Serializable
  data object StringType : XValueType

  @Serializable
  data class Other(val rawType: String) : XValueType

  @Serializable
  data object Unknown : XValueType
}