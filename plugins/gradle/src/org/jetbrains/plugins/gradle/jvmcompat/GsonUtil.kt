// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

// Gson utility functions that return null if an element is not the correct type, rather than throwing an exception

val JsonElement.asSafeJsonObject: JsonObject?
  get() = takeIf { it.isJsonObject }?.asJsonObject

val JsonElement.asSafeJsonArray: JsonArray?
  get() = takeIf { it.isJsonArray }?.asJsonArray

val JsonElement.asSafeString: String?
  get() = runCatching { asString }.getOrNull()

val JsonElement.asSafeInt: Int?
  get() = runCatching { asInt }.getOrNull()