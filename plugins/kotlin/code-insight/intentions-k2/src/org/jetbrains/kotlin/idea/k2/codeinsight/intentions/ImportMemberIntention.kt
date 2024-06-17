// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective

internal class ImportMemberIntention :
    KotlinApplicableModCommandAction<KtNameReferenceExpression, ImportMemberIntention.Context>(KtNameReferenceExpression::class),
    HighPriorityAction {

    data class Context(
        val fqName: FqName,
        val shortenCommand: ShortenCommand,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.import.for.member")

    override fun getActionName(
      actionContext: ActionContext,
      element: KtNameReferenceExpression,
      elementContext: Context,
    ): String = KotlinBundle.message("add.import.for.0", elementContext.fqName.asString())

    override fun isApplicableByPsi(element: KtNameReferenceExpression): Boolean =
        // Ignore simple name expressions or already imported names.
        element.getQualifiedElement() != element && !element.isInImportDirective()

    context(KaSession)
    override fun prepareContext(element: KtNameReferenceExpression): Context? {
        val symbol = element.mainReference.resolveToSymbol() ?: return null
        return computeContext(element, symbol)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtNameReferenceExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        elementContext.shortenCommand.invokeShortening()
    }
}

context(KaSession)
private fun computeContext(psi: KtNameReferenceExpression, symbol: KtSymbol): ImportMemberIntention.Context? {
    return when (symbol) {
        is KaConstructorSymbol,
        is KaClassOrObjectSymbol -> {
            val classId = if (symbol is KaClassOrObjectSymbol) {
                symbol.classId
            } else {
                (symbol as KaConstructorSymbol).containingClassId
            } ?: return null
            val shortenCommand = collectPossibleReferenceShortenings(
                psi.containingKtFile,
                classShortenStrategy = {
                    if (it.classId == classId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                }, callableShortenStrategy = {
                    if (it is KaConstructorSymbol && it.containingClassId == classId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                })
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Context(classId.asSingleFqName(), shortenCommand)
        }

        is KaCallableSymbol -> {
            val callableId = symbol.callableId ?: return null
            if (callableId.callableName.isSpecial) return null
            if (symbol.getImportableName() == null) return null
            val shortenCommand = collectPossibleReferenceShortenings(
                psi.containingKtFile,
                classShortenStrategy = { ShortenStrategy.DO_NOT_SHORTEN },
                callableShortenStrategy = {
                    if (it.callableId == callableId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                }
            )
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Context(callableId.asSingleFqName(), shortenCommand)
        }

        else -> return null
    }
}