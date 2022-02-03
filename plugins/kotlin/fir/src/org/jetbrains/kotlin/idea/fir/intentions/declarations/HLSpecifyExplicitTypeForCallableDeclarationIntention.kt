// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.intentions.declarations

import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.with
import org.jetbrains.kotlin.idea.fir.api.*
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.fir.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.psi.*

class HLSpecifyExplicitTypeForCallableDeclarationIntention :
    AbstractHLIntention<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.Type>(KtCallableDeclaration::class, applicator) {
    override val applicabilityRange: HLApplicabilityRange<KtCallableDeclaration> = ApplicabilityRanges.SELF

    override val inputProvider = inputProvider<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.Type> { declaration ->
        val returnType = declaration.getReturnKtType()
        val denotableType = returnType.approximateToSuperPublicDenotable() ?: returnType
        with(CallableReturnTypeUpdaterApplicator.Type) { createByKtType(denotableType) }
    }

    companion object {
        private val applicator = CallableReturnTypeUpdaterApplicator.applicator.with {
            isApplicableByPsi { declaration: KtCallableDeclaration ->
                if (declaration is KtConstructor<*> || declaration is KtFunctionLiteral) return@isApplicableByPsi false
                declaration.typeReference == null && (declaration as? KtNamedFunction)?.hasBlockBody() != true
            }
            familyAndActionName(KotlinBundle.lazyMessage("specify.return.type.explicitly"))
        }
    }
}