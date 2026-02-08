// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.fasterxml.jackson.annotation.JsonInclude
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class EventsScheme(
  val commitHash: String?,
  val buildNumber: String?,
  val scheme: List<GroupDescriptor>,
)

@ApiStatus.Internal
enum class FieldDataType { ARRAY, PRIMITIVE }

@ApiStatus.Internal
@Suppress("EqualsOrHashCode")
class FieldDataTypeIncludeFilter {
  override fun equals(other: Any?): Boolean {
    return other == null || other is FieldDataType && FieldDataType.PRIMITIVE == other
  }
}

@ApiStatus.Internal
data class FieldDescriptor(
  val path: String, // path="object1.object2.name"
  val value: Set<String>,
  @get:JsonInclude(JsonInclude.Include.NON_DEFAULT)
  val shouldBeAnonymized: Boolean = false,
  @get:JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FieldDataTypeIncludeFilter::class)
  val dataType: FieldDataType = FieldDataType.PRIMITIVE,
  val description: String? = null,
)

/**
 * [objectArrays] enumerates all arrays in the event data structure.
 * Required to reconstruct the data model from the metadata scheme.
 */
@ApiStatus.Internal
data class EventDescriptor(
  val event: String,
  val fields: Set<FieldDescriptor>,
  val description: String? = null,
  val objectArrays: List<String>? = null,
)

@ApiStatus.Internal
data class PluginSchemeDescriptor(val id: String)

@ApiStatus.Internal
data class GroupDescriptor(
  val id: String,
  val type: String,
  val version: Int,
  val schema: Set<EventDescriptor>,
  val className: String,
  val recorder: String,
  val plugin: PluginSchemeDescriptor,
  val description: String? = null,
  val fileName: String? = null,
)
