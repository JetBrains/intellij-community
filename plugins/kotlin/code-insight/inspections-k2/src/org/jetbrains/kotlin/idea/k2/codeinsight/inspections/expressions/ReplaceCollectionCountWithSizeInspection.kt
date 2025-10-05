// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val COLLECTION_COUNT_CALLABLE_ID = CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("count"))
private val COLLECTION_CLASS_IDS = setOf(StandardClassIds.Collection, StandardClassIds.Array, StandardClassIds.Map) +
        StandardClassIds.elementTypeByPrimitiveArrayType.keys + StandardClassIds.unsignedArrayTypeByElementType.keys

internal class ReplaceCollectionCountWithSizeInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.replace.collection.count.with.size.display.name")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.calleeExpression?.text == "count" && element.valueArguments.isEmpty()

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val functionSymbol = element.resolveToFunctionSymbol() ?: return null
        val receiverClassId = (functionSymbol.receiverType as? KaClassType)?.classId ?: return null
        return (functionSymbol.callableId == COLLECTION_COUNT_CALLABLE_ID
                && receiverClassId in COLLECTION_CLASS_IDS).asUnit
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.collection.count.with.size.quick.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            element.replace(KtPsiFactory(element.project).createExpression("size"))
        }
    }
}

context(_: KaSession)
private fun KtCallExpression.resolveToFunctionSymbol(): KaNamedFunctionSymbol? =
    calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol
