// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

internal class SpecifyTypeExplicitlyIntention :
    KotlinApplicableIntentionWithContext<KtCallableDeclaration, TypeInfo>(KtCallableDeclaration::class) {

    override fun getFamilyName(): String = KotlinBundle.message("specify.type.explicitly")
    override fun getActionName(element: KtCallableDeclaration, context: TypeInfo): String = when (element) {
        is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
        else -> KotlinBundle.message("specify.type.explicitly")
    }

    override fun getApplicabilityRange() = ApplicabilityRanges.DECLARATION_WITHOUT_INITIALIZER

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean {
        if (element is KtConstructor<*> || element is KtFunctionLiteral) return false
        return element.typeReference == null && (element as? KtNamedFunction)?.hasBlockBody() != true
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

        return getTypeInfo(element)
    }

    override fun apply(element: KtCallableDeclaration, context: TypeInfo, project: Project, editor: Editor?) =
        updateType(element, context, project, editor)
}