// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.util.NlsActions
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeExtendedInfo {
  val text: String?
  @get:NlsActions.ActionText
  val actionText: String?
  @get:NlsActions.ActionDescription
  val actionDescription: String?
  val keyCode: Int?
  val modifiers: Int?
}

@ApiStatus.Experimental
@ApiStatus.Internal
@Serializable
class SeExtendedInfoImpl(
  override val text: String?,
  @NlsActions.ActionText override val actionText: String?,
  @NlsActions.ActionDescription override val actionDescription: String?,
  override val keyCode: Int? = null,
  override val modifiers: Int? = null,
) : SeExtendedInfo