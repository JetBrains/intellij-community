// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereCommandInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
@Serializable
class SeCommandInfo (
  val command: String,
  val definition: String,
  val providerId: String,
) {
  constructor(searchEverywhereCommandInfo: SearchEverywhereCommandInfo, providerId: String) : this(
    command = searchEverywhereCommandInfo.command,
    definition = searchEverywhereCommandInfo.definition,
    providerId = providerId
  )
}