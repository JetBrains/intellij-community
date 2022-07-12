// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator.getTypeInfo
import org.jetbrains.kotlin.psi.*

class SpecifyTypeExplicitlyIntention :
    AbstractKotlinApplicatorBasedIntention<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.TypeInfo>(KtCallableDeclaration::class)
{
    override val applicabilityRange: KotlinApplicabilityRange<KtCallableDeclaration> = ApplicabilityRanges.DECLARATION_WITHOUT_INITIALIZER

    override val applicator: KotlinApplicator<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.TypeInfo>
        get() = CallableReturnTypeUpdaterApplicator.applicator.with {
            isApplicableByPsi { declaration: KtCallableDeclaration ->
                if (declaration is KtConstructor<*> || declaration is KtFunctionLiteral) return@isApplicableByPsi false
                declaration.typeReference == null && (declaration as? KtNamedFunction)?.hasBlockBody() != true
            }

            familyName(KotlinBundle.lazyMessage("specify.type.explicitly"))

            actionName { declaration, _ ->
                when (declaration) {
                    is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
                    else -> KotlinBundle.message("specify.type.explicitly")
                }
            }
        }

    override val inputProvider = inputProvider<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.TypeInfo> { declaration ->
        val diagnostics = declaration.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).map { it::class }.toSet()
        // Avoid redundant intentions
        if (KtFirDiagnostic.AmbiguousAnonymousTypeInferred::class in diagnostics ||
            KtFirDiagnostic.PropertyWithNoTypeNoInitializer::class in diagnostics ||
            KtFirDiagnostic.MustBeInitialized::class in diagnostics
        ) return@inputProvider null
        getTypeInfo(declaration)
    }
}