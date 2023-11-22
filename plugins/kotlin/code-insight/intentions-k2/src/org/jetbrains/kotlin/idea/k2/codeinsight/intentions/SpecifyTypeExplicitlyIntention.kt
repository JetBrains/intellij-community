// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

internal class SpecifyTypeExplicitlyIntention:
    AbstractKotlinModCommandWithContext<KtCallableDeclaration, TypeInfo>(KtCallableDeclaration::class) {

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallableDeclaration> =
        ApplicabilityRanges.DECLARATION_WITHOUT_INITIALIZER

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean {
        if (element is KtConstructor<*> || element is KtFunctionLiteral) return false
        if (element is KtParameter && element.isLoopParameter && element.destructuringDeclaration != null) return false
        return element.typeReference == null && (element as? KtNamedFunction)?.hasBlockBody() != true
    }

    override fun getFamilyName(): String = KotlinBundle.message("specify.type.explicitly")

    override fun getActionName(element: KtCallableDeclaration, context: TypeInfo): String = when (element) {
        is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
        else -> KotlinBundle.message("specify.type.explicitly")
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallableDeclaration): TypeInfo? {
        // Avoid redundant intentions
        val diagnostics = element.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        if (diagnostics.any { diagnostic ->
                diagnostic is KtFirDiagnostic.AmbiguousAnonymousTypeInferred
                        || diagnostic is KtFirDiagnostic.PropertyWithNoTypeNoInitializer
                        || diagnostic is KtFirDiagnostic.MustBeInitialized
        }) return null

        return invokeContext(element).takeUnless { it.defaultType.isError }
    }

    context(KtAnalysisSession)
    override fun invokeContext(element: KtCallableDeclaration): TypeInfo = getTypeInfo(element)

    override fun apply(element: KtCallableDeclaration, context: AnalysisActionContext<TypeInfo>, updater: ModPsiUpdater) {
        updateType(element, context.analyzeContext, element.project, updater = updater)
    }
}