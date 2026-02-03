// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTrySurrounderBase
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.TryCatchExceptionUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTryExpression

sealed class KotlinTryExpressionSurrounder : KotlinControlFlowExpressionSurrounderBase() {
    class TryCatch : KotlinTryExpressionSurrounder() {
        @NlsSafe
        override fun getTemplateDescription() = "try { expr } catch {}"

        override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
            surroundWithCatchBlock(context, expression, updater)
        }

        override fun getPattern(exceptionClasses: List<ClassId>): String = buildString {
            append("try { $0 } ")
            for (classId in exceptionClasses) {
                append("catch (e: ")
                append(classId.asFqNameString())
                append(") {\nthrow e\n}")
            }
        }
    }

    class TryCatchFinally : KotlinTryExpressionSurrounder() {
        @NlsSafe
        override fun getTemplateDescription() = "try { expr } catch {} finally {}"

        override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
            surroundWithCatchBlock(context, expression, updater)
        }

        override fun getPattern(exceptionClasses: List<ClassId>): String = buildString {
            append("try { $0 } ")
            for (classId in exceptionClasses) {
                append("catch (e: ")
                append(classId.asFqNameString())
                append(") {\nthrow e\n}")
            }
            append(" finally {\n}")
        }

    }

    class TryFinally : KotlinTryExpressionSurrounder() {
        @NlsSafe
        override fun getTemplateDescription() = "try { expr } finally {}"
        override fun getPattern(exceptionClasses: List<ClassId>) = "try { $0 } finally {\nb\n}"
        override fun getRange(context: ActionContext, replaced: KtExpression, updater: ModPsiUpdater): TextRange? {
            val blockExpression = (replaced as KtTryExpression).finallyBlock?.finalExpression ?: return null
            val stmt = blockExpression.statements[0]
            val range = stmt.textRange
            stmt.delete()
            val offset = range?.startOffset ?: return null
            return TextRange(offset, offset)
        }
    }

    override fun getRange(context: ActionContext, replaced: KtExpression, updater: ModPsiUpdater): TextRange? {
        val tryExpression = replaced as KtTryExpression
        return KotlinTrySurrounderBase.getCatchTypeParameter(tryExpression)?.textRange
    }

    protected fun surroundWithCatchBlock(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = org.jetbrains.kotlin.psi.KtPsiFactory(context.project)
        val exceptions = TryCatchExceptionUtil.collectPossibleExceptions(expression)

        val newElement = psiFactory.createExpression(getPattern(exceptions).replace("$0", expression.text))
        val replaced = expression.replace(newElement) as KtTryExpression

        shortenReferences(replaced, ShortenOptions.DEFAULT, classShortenStrategy = { ShortenStrategy.SHORTEN_AND_IMPORT })

        replaced.catchClauses
            .mapNotNull { it.catchParameter?.typeReference }
            .forEach { typeRef -> ShortenReferencesFacility.getInstance().shorten(replaced.containingKtFile, typeRef.textRange) }

        KotlinTrySurrounderBase.getCatchTypeParameter(replaced)?.let { updater.select(it) }
    }

}
