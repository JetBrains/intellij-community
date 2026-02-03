// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ReplaceStringFormatWithLiteralInspection.Context
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import java.util.LinkedList

private val placeHolder: Regex by lazy { "%".toRegex() }
private val stringPlaceHolder: Regex by lazy { "%s".toRegex() }

internal class ReplaceStringFormatWithLiteralInspection : KotlinApplicableInspectionBase.Simple<KtExpression, Context>() {

    class Context(val format: String, val replaceArgs: List<String>)

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        val callExpression = getApplicableCallExpression(element) ?: return false

        val args = callExpression.valueArguments.mapNotNull { it.getArgumentExpression() }
        if (args.size <= 1) return false

        val format = args[0].text
        if (format.startsWith("\"\"\"")) return false

        val placeHolders = placeHolder.findAll(format).toList()
        if (placeHolders.size != args.size - 1) return false
        placeHolders.forEach {
            val next = it.range.last + 1
            val nextStr = if (next < format.length) format.substring(next, next + 1) else null
            if (nextStr != "s") return false
        }
        return true
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val callExpression = getApplicableCallExpression(element) ?: return null
        val resolvedExpression = element.resolveExpression() ?: return null
        val fqName = resolvedExpression.importableFqName?.asString() ?: return null
        if (fqName != "kotlin.text.format" && fqName != "java.lang.String.format") return null

        val args = callExpression.valueArguments.mapNotNull { it.getArgumentExpression() }
        if (args.asSequence().drop(1).any { it.resolveExpression()?.isSubtypeOfFormattable() == true }) return null

        val format = args[0].text.removePrefix("\"").removeSuffix("\"")
        val replaceArgs = args.asSequence()
            .drop(1)
            .mapTo(mutableListOf()) {
                buildStringTemplateForExpression(it, true, null)
            }

        return Context(format, replaceArgs)
    }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("inspection.replace.string.format.with.literal.display.name")

    override fun createQuickFix(
        element: KtExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> = ReplaceWithStringLiteralFix(context)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor { callExpression ->
        if (callExpression.calleeExpression?.text != "format") return@callExpressionVisitor
        val qualifiedExpression = callExpression.parent as? KtQualifiedExpression
        if (qualifiedExpression != null && !qualifiedExpression.receiverExpression.text.endsWith("String")) return@callExpressionVisitor
        visitTargetElement(qualifiedExpression ?: callExpression, holder, isOnTheFly)
    }

    private class ReplaceWithStringLiteralFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.string.literal.fix.family.name")

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val remainingArgs = LinkedList(context.replaceArgs)

            val stringLiteral = stringPlaceHolder.replace(context.format) { remainingArgs.pop() }
            element
                .replaced(KtPsiFactory(project).createStringTemplate(stringLiteral))
                .entries
                .forEach {
                    val blockEntry = (it as? KtBlockStringTemplateEntry)
                    blockEntry?.dropCurlyBracketsIfPossible()
                }
        }
    }
}

private fun KaSymbol.isSubtypeOfFormattable(): Boolean {
    val callableSymbol = this as? KaCallableSymbol ?: return false
    val returnTypeSymbol = callableSymbol.returnType.symbol as? KaClassSymbol ?: return false
    return returnTypeSymbol.superTypes.any {
        (it as? KaClassType)?.classId?.asFqNameString() == "java.util.Formattable"
    }
}

private fun getApplicableCallExpression(element: KtExpression): KtCallExpression? {
    val qualifiedExpression = element as? KtQualifiedExpression
    return qualifiedExpression?.callExpression ?: (element as? KtCallExpression)
}