// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.Details
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalDetails(private val details: String) : Serializable, Details {

    constructor(details: Details) : this(details.details)

    override fun getDetails(): String = details
}
