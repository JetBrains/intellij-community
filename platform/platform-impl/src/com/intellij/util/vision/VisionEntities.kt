// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.vision

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class Container(val entities: List<Page>)

@ApiStatus.Internal
@Serializable
data class Page(
  val id: Int,
  val publicVars: List<PublicVar>,
  val actions: List<Action>,
  val languages: List<Language>,
  val html: String,
)

@ApiStatus.Internal
@Serializable
data class Action(val value: String, val description: String)

@ApiStatus.Internal
@Serializable
data class Language(val code: String)

@ApiStatus.Internal
@Serializable
data class PublicVar(val value: String, val description: String)
