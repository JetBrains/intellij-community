// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.starProjectionFixFamilyName
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference

internal object AddStarProjectionsFixFactory {
    val addStarProjectionsFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoTypeArgumentsOnRhs ->
        val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val classSymbol = diagnostic.classifier as? KtNamedClassOrObjectSymbol

        if (classSymbol?.isInner == true) {
            val targetClasses = getTargetClasses(typeReference, classSymbol)
            val replaceString = createReplaceString(targetClasses)
            return@ModCommandBased listOf(AddStartProjectionsForInnerClass(typeReference, replaceString))
        }

        val typeElement = typeReference.typeElement ?: return@ModCommandBased emptyList()
        val unwrappedType = StarProjectionUtils.getUnwrappedType(typeElement) ?: return@ModCommandBased emptyList()

        listOf(
            AddStarProjectionsFix(unwrappedType, diagnostic.expectedCount)
        )
    }

    private class AddStartProjectionsForInnerClass(
        element: KtTypeReference,
        val replaceString: String,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtTypeReference, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtTypeReference,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            val psiFactory = KtPsiFactory(actionContext.project)
            val replacement = psiFactory.createType(replaceString)
            element.replace(replacement)
        }

        override fun getFamilyName(): String = starProjectionFixFamilyName
    }
}

context(KtAnalysisSession)
private fun getTargetClasses(
    typeReference: KtTypeReference,
    classSymbol: KtClassOrObjectSymbol,
): List<KtNamedClassOrObjectSymbol> {
    val parentWithSelfClasses = classSymbol.parentsWithSelf.mapNotNull { it as? KtNamedClassOrObjectSymbol }.toList()

    val scope = typeReference.containingKtFile.getScopeContextForPosition(typeReference).getCompositeScope()
    val classSymbols = scope.getClassifierSymbols().filterIsInstance<KtNamedClassOrObjectSymbol>().toSet()

    val targets = parentWithSelfClasses.takeWhile {
        it.isInner || !classSymbols.contains(it)
    }

    val last = targets.lastOrNull() ?: return targets
    val next = parentWithSelfClasses.getOrNull(targets.size) ?: return targets

    return if (last.isInner && next.typeParameters.isNotEmpty() || !classSymbols.contains(last)) {
        targets + next
    } else {
        targets
    }
}

context(KtAnalysisSession)
private val KtDeclarationSymbol.parentsWithSelf: Sequence<KtDeclarationSymbol>
    get() = generateSequence(this) {
        it.getContainingSymbol()
    }

private fun createReplaceString(targetClasses: List<KtNamedClassOrObjectSymbol>): String {
    return targetClasses.mapIndexed { index, c ->
        val name = c.name.asString()
        val last = targetClasses.getOrNull(index - 1)
        val size = if (index == 0 || last?.isInner == true) c.typeParameters.size else 0
        if (size == 0) name else StarProjectionUtils.getTypeNameAndStarProjectionsString(name, size)
    }.reversed().joinToString(".")
}