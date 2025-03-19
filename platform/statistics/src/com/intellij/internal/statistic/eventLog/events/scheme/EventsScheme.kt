// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.fasterxml.jackson.annotation.JsonInclude

data class EventsScheme(val commitHash: String?,
                        val buildNumber: String?,
                        val scheme: List<GroupDescriptor>)

enum class FieldDataType { ARRAY, PRIMITIVE }

@Suppress("EqualsOrHashCode")
class FieldDataTypeIncludeFilter {
  override fun equals(other: Any?): Boolean {
    if (other == null) return true
    return other is FieldDataType && FieldDataType.PRIMITIVE == other
  }
}

data class FieldDescriptor(val path: String, // path="object1.object2.name"
                           val value: Set<String>,
                           @JsonInclude(JsonInclude.Include.NON_DEFAULT)
                           val shouldBeAnonymized: Boolean = false,
                           @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FieldDataTypeIncludeFilter::class)
                           val dataType: FieldDataType = FieldDataType.PRIMITIVE,
                           val description: String? = null)

/**
 * [objectArrays] enumerates all arrays in the event data structure.
 * Required to reconstruct the data model from the metadata scheme.
 */
data class EventDescriptor(val event: String,
                           val fields: Set<FieldDescriptor>,
                           val description: String? = null,
                           val objectArrays: List<String>? = null)

data class PluginSchemeDescriptor(val id: String)

data class GroupDescriptor(val id: String,
                           val type: String,
                           val version: Int,
                           val schema: Set<EventDescriptor>,
                           val className: String,
                           val recorder: String,
                           val plugin: PluginSchemeDescriptor,
                           val description: String? = null,
                           val fileName: String? = null)