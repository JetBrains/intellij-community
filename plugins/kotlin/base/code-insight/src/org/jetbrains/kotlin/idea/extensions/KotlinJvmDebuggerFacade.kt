// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.extensions

import com.intellij.openapi.components.serviceOrNull

interface KotlinJvmDebuggerFacade {
    companion object {
        val instance: KotlinJvmDebuggerFacade?
            get() = serviceOrNull()
    }

    val isCoroutineAgentAllowedInDebug: Boolean
}