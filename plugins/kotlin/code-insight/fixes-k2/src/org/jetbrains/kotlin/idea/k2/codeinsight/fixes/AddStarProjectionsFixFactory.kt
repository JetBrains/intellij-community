// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.starProjectionFixFamilyName
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference

internal object AddStarProjectionsFixFactory {
    val addStarProjectionsFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoTypeArgumentsOnRhs ->
        val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val classSymbol = diagnostic.classifier as? KaNamedClassSymbol

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
    ) : PsiUpdateModCommandAction<KtTypeReference>(element) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtTypeReference,
            updater: ModPsiUpdater,
        ) {
            val psiFactory = KtPsiFactory(actionContext.project)
            val replacement = psiFactory.createType(replaceString)
            element.replace(replacement)
        }

        override fun getFamilyName(): String = starProjectionFixFamilyName
    }
}

context(KaSession)
private fun getTargetClasses(
    typeReference: KtTypeReference,
    classSymbol: KaClassSymbol,
): List<KaNamedClassSymbol> {
    val parentWithSelfClasses = classSymbol.parentsWithSelf.mapNotNull { it as? KaNamedClassSymbol }.toList()

    val scope = typeReference.containingKtFile.scopeContext(typeReference).compositeScope()
    val classSymbols = scope.classifiers.filterIsInstance<KaNamedClassSymbol>().toSet()

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

context(KaSession)
private val KaDeclarationSymbol.parentsWithSelf: Sequence<KaDeclarationSymbol>
    get() = generateSequence(this) { it.containingDeclaration }

private fun createReplaceString(targetClasses: List<KaNamedClassSymbol>): String {
    return targetClasses.mapIndexed { index, c ->
        val name = c.name.asString()
        val last = targetClasses.getOrNull(index - 1)
        val size = if (index == 0 || last?.isInner == true) c.typeParameters.size else 0
        if (size == 0) name else StarProjectionUtils.getTypeNameAndStarProjectionsString(name, size)
    }.reversed().joinToString(".")
}