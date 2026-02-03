// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isTopLevel
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectPossibleReferenceShorteningsForIde
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveCompanionObjectShortReferenceToContainingClassSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective

@OptIn(KaIdeApi::class)
internal class ImportMemberIntention :
    KotlinApplicableModCommandAction<KtElement, ImportMemberIntention.Context>(KtElement::class) {

    data class Context(
        val fqName: FqName,
        val shortenCommand: ShortenCommand,
    )

    override fun getFamilyName(): String =
        KotlinBundle.message("add.import.for.member")

    override fun getPresentation(
        context: ActionContext,
        element: KtElement,
    ): Presentation? {
        val (fqName) = getElementContext(context, element)
            ?: return null
        return Presentation.of(KotlinBundle.message("add.import.for.0", fqName.asString()))
            .withPriority(PriorityAction.Priority.HIGH)
    }

    override fun isApplicableByPsi(element: KtElement): Boolean =
        (element is KtDotQualifiedExpression && !element.isInImportDirective()) || element is KtUserType

    override fun KaSession.prepareContext(element: KtElement): Context? {
        val reference = element.actualReference ?: return null

        val symbol =
            // for implicit companion object references, we want to import the outer class
            reference.resolveCompanionObjectShortReferenceToContainingClassSymbol()
                ?: reference.resolveToSymbol()
                ?: return null
        
        val file = element.containingKtFile
        return computeContext(file, symbol)?.takeUnless {
            symbol.isTopLevel && symbol.containingFile?.isInSamePackage(file) == true
        }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtElement,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        elementContext.shortenCommand.invokeShortening()
    }
}

context(_: KaSession)
@OptIn(KaIdeApi::class)
private fun computeContext(file: KtFile, symbol: KaSymbol): ImportMemberIntention.Context? {
    return when (symbol) {
        is KaConstructorSymbol,
        is KaClassSymbol -> {
            val classId = if (symbol is KaClassSymbol) {
                symbol.classId
            } else {
                (symbol as KaConstructorSymbol).containingClassId
            } ?: return null
            val shortenCommand = collectPossibleReferenceShorteningsForIde(
                file,
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
            if (symbol.importableFqName == null) return null
            val shortenCommand = collectPossibleReferenceShorteningsForIde(
                file,
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

private fun KaFileSymbol.isInSamePackage(file: KtFile): Boolean =
    (psi as? KtFile)?.packageFqName == file.packageFqName
