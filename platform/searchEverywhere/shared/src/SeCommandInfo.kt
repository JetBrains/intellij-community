// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereCommandInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeCommandInfo {
  val command: String
  val definition: String
  val providerId: String
}

@ApiStatus.Experimental
class SeCommandInfoFactory {
  fun create(searchEverywhereCommandInfo: SearchEverywhereCommandInfo, providerId: String): SeCommandInfo =
    SeCommandInfoImpl(command = searchEverywhereCommandInfo.command,
                      definition = searchEverywhereCommandInfo.definition,
                      providerId = providerId)

  fun create(command: String, definition: String, providerId: String): SeCommandInfo =
    SeCommandInfoImpl(command, definition, providerId)
}

@ApiStatus.Internal
@Serializable
internal class SeCommandInfoImpl internal constructor(
  override val command: String,
  override val definition: String,
  override val providerId: String,
) : SeCommandInfo