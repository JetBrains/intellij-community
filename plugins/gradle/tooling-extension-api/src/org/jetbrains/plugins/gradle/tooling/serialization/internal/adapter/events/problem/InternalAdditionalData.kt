// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.AdditionalData
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalAdditionalData(private val data: Map<String, Any>) : Serializable, AdditionalData {

  constructor(data: AdditionalData) : this(data.asMap)

  override fun getAsMap(): Map<String, Any> = data
}
