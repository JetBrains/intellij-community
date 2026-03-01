// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern

private val HASH_MAP_CREATION_FQ_NAMES: Set<String> = setOf(
    "java.util.HashMap",
    "kotlin.collections.HashMap",
    "kotlin.collections.hashMapOf",
)

private val HASH_MAP_CREATION_SHORT_NAMES: Set<String> = setOf(
    "HashMap",
    "hashMapOf",
)

internal class ReplaceWithEnumMapInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ReplaceWithEnumMapInspection.Context>() {

    @JvmInline
    value class Context(val enumClassName: String)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (!element.platform.isJvm()) return false
        if (element.valueArguments.isNotEmpty()) return false
        val calleeText = element.calleeExpression?.text ?: return false
        return calleeText in HASH_MAP_CREATION_FQ_NAMES ||
                calleeText in HASH_MAP_CREATION_SHORT_NAMES ||
                element.containingKtFile.importDirectives.any {
                    val importedFqName = it.importedFqName?.asString() ?: return@any false
                    importedFqName in HASH_MAP_CREATION_FQ_NAMES && calleeText == it.aliasName
                }
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replaceable.with.enummap")

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val symbol = element.resolveToCall()?.successfulFunctionCallOrNull()?.symbol

        val fqName = when (symbol) {
            is KaConstructorSymbol -> symbol.containingClassId?.asFqNameString()
            is KaNamedFunctionSymbol -> symbol.importableFqName?.asString()
            else -> null
        } ?: return null

        if (fqName !in HASH_MAP_CREATION_FQ_NAMES) return null

        val expectedType = element.expectedType ?: return null
        val firstArgType = (expectedType as? KaClassType)?.typeArguments?.firstOrNull()?.type ?: return null
        if (!firstArgType.isEnum()) return null
        val enumClassName = firstArgType.symbol?.classId?.asFqNameString() ?: return null

        return Context(enumClassName)
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.enum.map.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val elementToReplace = element.parent
                .takeIf { (it as? KtQualifiedExpression)?.selectorExpression == element }
                ?: element

            val factory = KtPsiFactory(project)
            val newExpression = elementToReplace.replace(
                factory.createExpressionByPattern(
                    "java.util.EnumMap($0::class.java)",
                    context.enumClassName,
                )
            ) as KtExpression
            shortenReferences(newExpression)
        }
    }
}
