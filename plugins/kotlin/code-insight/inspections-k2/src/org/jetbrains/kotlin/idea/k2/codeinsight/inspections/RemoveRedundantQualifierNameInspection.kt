// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionWithoutClassInstanceAsReceiver
import org.jetbrains.kotlin.idea.codeinsight.utils.isReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import javax.swing.JComponent

class RemoveRedundantQualifierNameInspection :
    AbstractKotlinApplicableInspectionWithContext<KtElement, RemoveRedundantQualifierNameInspection.Context>(KtElement::class),
    CleanupLocalInspectionTool {
    class Context(
        val shortenings: List<ShortenCommand>
    )

    override fun getProblemDescription(element: KtElement, context: Context): String =
        KotlinBundle.message("redundant.qualifier.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("remove.redundant.qualifier.name.quick.fix.text")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtElement> = ApplicabilityRanges.SELF

    override fun apply(element: KtElement, context: Context, project: Project, editor: Editor?) {
        context.shortenings.forEach { it.invokeShortening() }
    }

    context(KtAnalysisSession) override fun prepareContext(element: KtElement): Context? = when (element) {
        is KtDotQualifiedExpression -> prepareContextForQualifier(element)
        is KtUserType -> prepareContextForUserType(element)
        else -> null
    }

    context(KtAnalysisSession) private fun prepareContextForQualifier(expression: KtDotQualifiedExpression): Context? {
        var expressionForAnalyze = expression.expressionWithoutClassInstanceAsReceiver() ?: return null
        val receiver = expressionForAnalyze.receiverExpression
        val receiverDeclaration = receiver.getQualifiedElementSelectorDeclaration()
        when {
            isEnumCompanionObject(receiverDeclaration) -> if (receiver is KtDotQualifiedExpression) {
                if (isEnumClass(receiver.receiverExpression.getQualifiedElementSelectorDeclaration())) return null

                /**
                 * TODO: We guess that there is no example actually goes to this line. Make sure whether it is true or not, and
                 *       drop the following line.
                 * Note that the receiver makes `isEnumCompanionObject(receiverDeclaration)` true must be `(an enum class).Companion`.
                 * Therefore, `receiver.receiverExpression.getQualifiedElementSelectorDeclaration()` is expected to be an enum class.
                 */
                expressionForAnalyze = receiver
            }

            isEnumClass(receiverDeclaration) -> {
                val hasCompanion = expressionForAnalyze.selectorExpression?.getQualifiedElementSelectorDeclaration()
                    ?.let { isEnumCompanionObject(it) } == true
                val callingBuiltInEnumFunction = expressionForAnalyze.isReferenceToBuiltInEnumFunction()
                when {
                    receiver is KtDotQualifiedExpression -> expressionForAnalyze = receiver
                    hasCompanion || callingBuiltInEnumFunction -> return null
                }
            }
        }

        while (true) {
            val symbol = expressionForAnalyze.getQualifiedElementSelector()?.mainReference?.resolveToSymbol() ?: return null
            if (symbol !is KtCallableSymbol && symbol !is KtClassLikeSymbol) return null
            val shortenCommand = tryShorteningExpression(expressionForAnalyze, symbol)
            if (!shortenCommand.isEmpty) {
                val shortenings = mutableListOf(shortenCommand)
                return Context(shortenings)
            }
            expressionForAnalyze = expressionForAnalyze.receiverExpression as? KtDotQualifiedExpression ?: return null
        }
    }

    context(KtAnalysisSession) private fun tryShorteningExpression(expression: KtDotQualifiedExpression, symbol: KtSymbol) =
        collectPossibleReferenceShortenings(
            expression.containingKtFile,
            TextRange(expression.startOffset, expression.endOffset),
            {
                if (it == symbol) ShortenOption.SHORTEN_IF_ALREADY_IMPORTED
                else ShortenOption.DO_NOT_SHORTEN
            }, {
                if (it == symbol) ShortenOption.SHORTEN_IF_ALREADY_IMPORTED
                else ShortenOption.DO_NOT_SHORTEN
            }
        )

    context(KtAnalysisSession) private fun prepareContextForUserType(type: KtUserType): Context? {
        val shortenCommand = collectPossibleReferenceShortenings(
            type.containingKtFile,
            TextRange(type.startOffset, type.endOffset),
            { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED },
            { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED },
        )
        if (!shortenCommand.isEmpty) {
            val shortenings = mutableListOf(shortenCommand)
            return Context(shortenings)
        }
        return null
    }

    override fun isApplicableByPsi(element: KtElement): Boolean {
        return when (element) {
            is KtDotQualifiedExpression -> element.isApplicableByPsi()
            is KtUserType -> {
                val selector = element.getQualifiedElementSelector() ?: return false
                if (selector.text == element.text) return false
                element.parent !is KtUserType
            }
            else -> false
        }
    }

    fun KtDotQualifiedExpression.isApplicableByPsi(): Boolean {
        /**
         * The following three lines avoid handling [KtDotQualifiedExpression] who is a child of [KtDotQualifiedExpression],
         * package directive, and import directive.
         *
         * For example, for the following code,
         *
         * package a.b.c
         * import x.y.z
         * ...
         * val foo = x.y.z.bar()
         *
         * It will skip `x.y.z` in `x.y.z.bar()`, `a.b.c` in `package a.b.c`, and `x.y.z` in `import x.y.z` while it handles
         * `x.y.z.bar()`. Note that `x.y.z` is [KtDotQualifiedExpression], but its parent `x.y.z.bar()` is also
         * [KtDotQualifiedExpression].
         */
        val expressionParent = parent
        if (expressionParent is KtDotQualifiedExpression || expressionParent is KtPackageDirective || expressionParent is KtImportDirective) return false
        var expressionForAnalyze = expressionWithoutClassInstanceAsReceiver() ?: return false

        /**
         * This will avoid the situation where removing a qualifier results in a name conflict.
         * For example, in the following code:
         *   val foo = package.name.ClassXYZ.foo
         * if we drop `package.name.ClassXYZ` from `package.name.ClassXYZ.foo()`, it becomes `val foo = foo`
         */
        return expressionForAnalyze.selectorExpression?.text != expressionParent.getNonStrictParentOfType<KtProperty>()?.name
    }

}

context(KtAnalysisSession)
private fun KtExpression.getQualifiedElementSelectorDeclaration() =
    getQualifiedElementSelector()?.mainReference?.resolveToSymbol()

private fun isEnumClass(symbol: KtSymbol?): Boolean {
    val classSymbol = symbol as? KtClassOrObjectSymbol ?: return false
    return classSymbol.classKind == KtClassKind.ENUM_CLASS
}

private fun isEnumCompanionObject(symbol: KtSymbol?): Boolean {
    val classSymbol = symbol as? KtClassOrObjectSymbol ?: return false
    return classSymbol.classKind == KtClassKind.COMPANION_OBJECT && classSymbol.psi?.getNonStrictParentOfType<KtClass>()?.isEnum() == true
}