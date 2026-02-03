// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.diagnostics
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Intention to specify type explicitly for callable declarations.
 *
 * If [useTemplate] is `true`, the intention will provide user with a template to select a specific type to insert;
 * otherwise, the inferred type will be inserted right away. See [TypeInfo.useTemplate] for the implementation.
 *
 * By default, and when instantiated by IntelliJ IDEA, [useTemplate] is set to `true`.
 */
@ApiStatus.Internal
class SpecifyTypeExplicitlyIntention @JvmOverloads constructor(private val useTemplate: Boolean = true) :
    KotlinApplicableModCommandAction<KtCallableDeclaration, TypeInfo>(KtCallableDeclaration::class) {

    override fun getApplicableRanges(element: KtCallableDeclaration): List<TextRange> =
        ApplicabilityRanges.declarationWithoutInitializer(element)

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean {
        if (element is KtConstructor<*> || element is KtFunctionLiteral) return false
        if (element is KtParameter && element.isLoopParameter && element.destructuringDeclaration != null) return false
        return element.typeReference == null && (element as? KtNamedFunction)?.hasBlockBody() != true
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun skip(element: KtCallableDeclaration): Boolean =
        element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            .any { diagnostic ->
                diagnostic is KaFirDiagnostic.AmbiguousAnonymousTypeInferred
                        || diagnostic is KaFirDiagnostic.PropertyWithNoTypeNoInitializer
                        || diagnostic is KaFirDiagnostic.MustBeInitialized
            }

    override fun getFamilyName(): String =
        KotlinBundle.message("specify.type.explicitly")

    override fun getPresentation(
        context: ActionContext,
        element: KtCallableDeclaration,
    ): Presentation {
        val actionName = when (element) {
            is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
            else -> KotlinBundle.message("specify.type.explicitly")
        }
        return Presentation.of(actionName)
    }

    override fun KaSession.prepareContext(element: KtCallableDeclaration): TypeInfo? =
        if (skip(element)) {
            null
        } else {
            getTypeInfo(element, useSmartCastType = true, useTemplate).takeUnless { it.defaultType.isError }
        }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallableDeclaration,
        elementContext: TypeInfo,
        updater: ModPsiUpdater,
    ) {
        updateType(element, elementContext, element.project, updater)
    }
}