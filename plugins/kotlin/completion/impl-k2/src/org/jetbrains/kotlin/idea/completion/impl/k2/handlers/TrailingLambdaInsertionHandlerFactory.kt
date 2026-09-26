// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType

/**
 * A temporary indirection that allows to disable [TrailingLambdaInsertionHandler] in cases where it is not desireable.
 * Use [Disabled] implementation for that.
 *
 * TODO Should be removed after LSP-1033 is fixed.
 */
internal interface TrailingLambdaInsertionHandlerFactory {
    context(_: KaSession)
    fun create(functionType: KaFunctionType): TrailingLambdaInsertionHandler?
    
    companion object {
        @JvmStatic
        fun getInstance(): TrailingLambdaInsertionHandlerFactory = service<TrailingLambdaInsertionHandlerFactory>()
    }
    
    class Default : TrailingLambdaInsertionHandlerFactory {
        context(_: KaSession)
        override fun create(functionType: KaFunctionType): TrailingLambdaInsertionHandler? =
            TrailingLambdaInsertionHandler.create(functionType)
    }
    
    class Disabled : TrailingLambdaInsertionHandlerFactory {
        context(_: KaSession)
        override fun create(functionType: KaFunctionType): TrailingLambdaInsertionHandler? = null
    }
}
