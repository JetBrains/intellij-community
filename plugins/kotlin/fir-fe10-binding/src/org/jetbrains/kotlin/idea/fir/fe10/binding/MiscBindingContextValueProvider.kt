// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.fe10.binding

import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.idea.fir.fe10.withAnalysisSession
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

class MiscBindingContextValueProvider(bindingContext: KtSymbolBasedBindingContext) {
    private val context = bindingContext.context

    init {
        bindingContext.registerGetterByKey(BindingContext.TYPE, this::getType)
    }

    private fun getType(ktTypeReference: KtTypeReference): KotlinType {
        return context.withAnalysisSession {
            ktTypeReference.getKtType()
        }.toKotlinType(context)
    }
}