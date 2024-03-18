// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import org.intellij.lang.annotations.Pattern

internal const val VALID_EVENT_ID = "[a-z0-9.]*[a-z0-9]+"
internal const val VALID_FIELD_NAME = "[a-z0-9_]*[a-z0-9]+"

@Pattern(VALID_EVENT_ID)
annotation class EventIdName
@Pattern(VALID_FIELD_NAME)
annotation class EventFieldName