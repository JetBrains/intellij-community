// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeEntry

internal object SuperClassNotInitializedFactories {
    val addParenthesis = diagnosticModCommandFixFactory(KtFirDiagnostic.SupertypeNotInitialized::class) factory@{ diagnostic ->
        val superTypeEntry = diagnostic.psi.parent as? KtSuperTypeEntry ?: return@factory emptyList()
        val superClassSymbol = diagnostic.psi.getKtType().expandedClassSymbol as? KtNamedClassOrObjectSymbol ?: return@factory emptyList()

        if (!superClassSymbol.isInheritableWithSuperConstructorCall(superTypeEntry)) {
            return@factory emptyList()
        }

        val constructors = superClassSymbol.getDeclaredMemberScope().getConstructors()
        buildList {
            add(AddParenthesisFix(superTypeEntry, moveCaretIntoParenthesis = constructors.any { it.valueParameters.isNotEmpty() }))
        }
    }

    context(KtAnalysisSession)
    private fun KtNamedClassOrObjectSymbol.isInheritableWithSuperConstructorCall(superTypeEntry: KtSuperTypeEntry): Boolean {
        if (classKind != KtClassKind.CLASS) return false
        return when (modality) {
            Modality.FINAL -> false
            Modality.OPEN -> true
            Modality.ABSTRACT -> true
            Modality.SEALED -> {
                val subClass = superTypeEntry.parentOfType<KtClassOrObject>()
                subClass?.isLocal == false
                        && classIdIfNonLocal?.packageFqName == superTypeEntry.containingKtFile.packageFqName
                        && getContainingModule() == useSiteModule
            }
        }
    }

    private class AddParenthesisFix(
        superTypeEntry: KtSuperTypeEntry,
        private val moveCaretIntoParenthesis: Boolean
    ) : PsiUpdateModCommandAction<KtSuperTypeEntry>(superTypeEntry), HighPriorityAction {

        override fun invoke(context: ActionContext, element: KtSuperTypeEntry, updater: ModPsiUpdater) {
            val withParenthesis = element.replaced(KtPsiFactory(context.project).createSuperTypeCallEntry(element.text + "()"))
            if (moveCaretIntoParenthesis) {
                withParenthesis.valueArgumentList?.leftParenthesis?.endOffset?.let { offset ->
                    updater.moveCaretTo(offset)
                }
            }
        }

        override fun getFamilyName() = KotlinBundle.message("change.to.constructor.invocation")
    }
}