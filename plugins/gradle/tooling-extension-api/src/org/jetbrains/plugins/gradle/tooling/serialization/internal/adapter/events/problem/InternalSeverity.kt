// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.Severity
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalSeverity(private val severity: Int, private val isKnown: Boolean) : Serializable, Severity {

  constructor(severity: Severity) : this(severity.severity, severity.isKnown)

  override fun getSeverity(): Int = severity

  override fun isKnown(): Boolean = isKnown
}
