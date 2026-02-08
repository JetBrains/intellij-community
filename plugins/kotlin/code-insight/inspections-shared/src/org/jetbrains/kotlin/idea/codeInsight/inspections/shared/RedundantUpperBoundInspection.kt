// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports redundant explicit upper bound `Any?` on a type parameter, e.g. `fun <T : Any?> foo(t: T) {}`
 * where `Any?` is the default upper bound in Kotlin.
 */
class RedundantUpperBoundInspection : KotlinApplicableInspectionBase.Simple<KtTypeParameter, Unit>() {

    override fun getProblemDescription(element: KtTypeParameter, context: Unit): @InspectionMessage String =
        KotlinBundle.message("inspection.redundant.upper.bound.problem")

    override fun createQuickFix(element: KtTypeParameter, context: Unit): KotlinModCommandQuickFix<KtTypeParameter> =
        object : KotlinModCommandQuickFix<KtTypeParameter>() {
            override fun getFamilyName(): String =
                KotlinBundle.message("inspection.redundant.upper.bound.fix.text")

            override fun applyFix(project: Project, element: KtTypeParameter, updater: ModPsiUpdater) {
                val bound = element.extendsBound ?: return
                removeUpperBoundWithColon(element, bound)
            }
        }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitTypeParameter(parameter: KtTypeParameter) {
                visitTargetElement(parameter, holder, isOnTheFly)
            }
        }

    override fun isApplicableByPsi(element: KtTypeParameter): Boolean =
        element.extendsBound != null

    override fun KaSession.prepareContext(element: KtTypeParameter): Unit? {
        val bound = element.extendsBound ?: return null
        return bound.type.isNullableAnyType().asUnit
    }

    private fun removeUpperBoundWithColon(element: KtTypeParameter, bound: KtTypeReference) {
        element.node.findChildByType(KtTokens.COLON)?.psi?.delete()
        bound.delete()
    }

    override fun getApplicableRanges(element: KtTypeParameter): List<TextRange> {
        val bound = element.extendsBound ?: return emptyList()
        return ApplicabilityRange.single(element) { bound }
    }
}