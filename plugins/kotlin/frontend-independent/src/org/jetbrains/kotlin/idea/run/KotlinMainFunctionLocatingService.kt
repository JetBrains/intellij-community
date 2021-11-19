/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A service for detecting entry points (like "main" function) in classes and objects.
 *
 * Abstracts away the usage of the different Kotlin frontends (detecting "main" requires resolve).
 *
 * See also [org.jetbrains.kotlin.idea.MainFunctionDetector] for the FE10-specific version of this service.
 */
interface KotlinMainFunctionLocatingService {
    fun isMain(function: KtNamedFunction): Boolean

    fun hasMain(declarations: List<KtDeclaration>): Boolean

    companion object {
        fun getInstance(): KotlinMainFunctionLocatingService = service()
    }
}