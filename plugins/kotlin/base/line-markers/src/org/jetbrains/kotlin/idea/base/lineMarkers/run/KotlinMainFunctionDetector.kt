// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.lineMarkers.run

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

@ApiStatus.Internal
interface KotlinMainFunctionDetector {
    fun isMain(function: KtNamedFunction): Boolean

    fun hasMain(declarations: List<KtDeclaration>): Boolean

    companion object {
        fun getInstance(): KotlinMainFunctionDetector = service()
    }
}