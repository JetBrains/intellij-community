// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtTypeReference

internal object SuperClassNotInitializedFactories {

    val addParenthesis = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SupertypeNotInitialized ->
        val typeReference = diagnostic.psi as? KtTypeReference
            ?: return@ModCommandBased emptyList()
        val superTypeEntry = typeReference.parent as? KtSuperTypeEntry
            ?: return@ModCommandBased emptyList()
        val superClassSymbol = typeReference.type.expandedSymbol as? KaNamedClassSymbol
            ?: return@ModCommandBased emptyList()

        if (!superClassSymbol.isInheritableWithSuperConstructorCall(superTypeEntry)) {
            return@ModCommandBased emptyList()
        }

        val constructors = superClassSymbol.declaredMemberScope.constructors
        buildList {
            add(AddParenthesisFix(superTypeEntry, moveCaretIntoParenthesis = constructors.any { it.valueParameters.isNotEmpty() }))
        }
    }

    context(KaSession)
    private fun KaNamedClassSymbol.isInheritableWithSuperConstructorCall(superTypeEntry: KtSuperTypeEntry): Boolean {
        if (classKind != KaClassKind.CLASS) return false
        return when (modality) {
            KaSymbolModality.FINAL -> false
            KaSymbolModality.OPEN -> true
            KaSymbolModality.ABSTRACT -> true
            KaSymbolModality.SEALED -> {
                val subClass = superTypeEntry.parentOfType<KtClassOrObject>()
                subClass?.isLocal == false
                        && classId?.packageFqName == superTypeEntry.containingKtFile.packageFqName
                        && containingModule == useSiteModule
            }
        }
    }

    private data class ElementContext(
        val moveCaretIntoParenthesis: Boolean,
    )

    private class AddParenthesisFix(
        element: KtSuperTypeEntry,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtSuperTypeEntry, ElementContext>(element, elementContext) {

        constructor(
            element: KtSuperTypeEntry,
            moveCaretIntoParenthesis: Boolean,
        ) : this(
            element,
            ElementContext(moveCaretIntoParenthesis),
        )

        override fun invoke(
            actionContext: ActionContext,
            element: KtSuperTypeEntry,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val withParenthesis = element.replaced(KtPsiFactory(actionContext.project).createSuperTypeCallEntry(element.text + "()"))
            if (elementContext.moveCaretIntoParenthesis) {
                withParenthesis.valueArgumentList?.leftParenthesis?.endOffset?.let { offset ->
                    updater.moveCaretTo(offset)
                }
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("change.to.constructor.invocation")
        override fun getPresentation(context: ActionContext, element: KtSuperTypeEntry): Presentation =
            Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)
    }
}