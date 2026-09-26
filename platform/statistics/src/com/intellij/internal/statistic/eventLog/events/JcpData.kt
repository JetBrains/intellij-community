// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import org.jetbrains.annotations.ApiStatus

/**
 * Structured payload attached to a FUS event solely for delivery to external (JCP) statistics listeners.
 *
 * Unlike regular event fields, this payload is **never validated**, **never sent to the FUS server**, and is
 * **excluded from the generated events scheme**. It is meant to be consumed through the raw-data listener channel only.
 *
 * For now it carries a flat map of strings; JCP can extend it with the value types it needs.
 * Carry the field via [EventFields.Jcp].
 */
@ApiStatus.Internal
data class JcpData(
  val strings: Map<String, String> = emptyMap(),
)
