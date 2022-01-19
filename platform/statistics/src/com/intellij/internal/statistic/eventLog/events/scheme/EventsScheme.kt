// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

data class EventsScheme(val commitHash: String?,
                        val buildNumber: String?,
                        val scheme: List<GroupDescriptor>)

enum class FieldDataType { ARRAY, PRIMITIVE }

data class FieldDescriptor(val path: String,
                           val value: Set<String>,
                           val dataType: FieldDataType = FieldDataType.PRIMITIVE)

data class EventDescriptor(val event: String,
                           val fields: Set<FieldDescriptor>)

data class GroupDescriptor(val id: String,
                           val type: String,
                           val version: Int,
                           val schema: Set<EventDescriptor>,
                           val className: String)