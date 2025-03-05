// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.backend.presentation.TargetPresentation
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeItemPresentation {
  val text: String
}

@ApiStatus.Internal
@Serializable
class SeTextItemPresentation(override val text: String): SeItemPresentation

@ApiStatus.Internal
@Serializable
data class SeActionItemPresentation(
  // TODO: There is a problem with serialization of Icon (CachedIconImage to be concrete)
  //val icon: Icon? = null,
  override val text: String,
  val location: String? = null,
  val switcherState: Boolean? = null,
  val isEnabled: Boolean = true,
  val shortcut: String? = null,
): SeItemPresentation

@ApiStatus.Internal
@Serializable
class SeTargetItemPresentation(val targetPresentation: TargetPresentation): SeItemPresentation {
  override val text: String = targetPresentation.presentableText
}
