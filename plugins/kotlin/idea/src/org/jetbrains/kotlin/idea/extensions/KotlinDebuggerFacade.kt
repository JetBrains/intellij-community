// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions

import com.intellij.openapi.components.serviceOrNull

interface KotlinJvmDebuggerFacade {
    companion object {
        val instance: KotlinJvmDebuggerFacade?
            get() = serviceOrNull()
    }

    val isCoroutineAgentAllowedInDebug: Boolean
}