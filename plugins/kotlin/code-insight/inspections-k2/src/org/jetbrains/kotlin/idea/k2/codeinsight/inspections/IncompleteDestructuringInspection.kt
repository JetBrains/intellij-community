// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

internal class IncompleteDestructuringInspection :
    KotlinApplicableInspectionBase.Simple<KtDestructuringDeclaration, IncompleteDestructuringInspection.Context>() {

    data class Context(
        val additionalEntries: List<KtDestructuringDeclarationEntry>,
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        destructuringDeclarationVisitor {
            visitTargetElement(it, holder, isOnTheFly)
        }

    override fun getProblemDescription(
        element: KtDestructuringDeclaration,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("incomplete.destructuring.declaration.text")

    override fun getApplicableRanges(element: KtDestructuringDeclaration): List<TextRange> =
        ApplicabilityRanges.destructuringDeclarationParens(element)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtDestructuringDeclaration): Context? {
        val primaryParameters = extractPrimaryParameters(element) ?: return null
        val currentEntries = element.entries
        if (currentEntries.size >= primaryParameters.size) return null

        val names = generateNames(element, primaryParameters)
        val types = generateTypesIfNeeded(element, primaryParameters)

        val psiFactory = KtPsiFactory(element.project)

        val additionalEntries = names
            .zip(types)
            .map { (name, type) -> "$name: $type" }
            .ifEmpty { names }
            .drop(currentEntries.size)
            .let { psiFactory.createDestructuringDeclaration("val (${it.joinToString(separator = ",")}) = TODO()").entries }

        return Context(additionalEntries)
    }


    override fun createQuickFix(
        element: KtDestructuringDeclaration,
        context: Context,
    ): KotlinModCommandQuickFix<KtDestructuringDeclaration> = object : KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("incomplete.destructuring.fix.family.name")

        override fun applyFix(
            project: Project,
            element: KtDestructuringDeclaration,
            updater: ModPsiUpdater,
        ): Unit = addMissingEntries(element, context)

        private fun addMissingEntries(
            destructuringDeclaration: KtDestructuringDeclaration,
            context: Context,
        ) {
            val psiFactory = KtPsiFactory(destructuringDeclaration.project)
            val currentEntries = destructuringDeclaration.entries

            val rPar = destructuringDeclaration.rPar
            val hasTrailingComma = destructuringDeclaration.trailingComma != null
            val currentEntriesIsEmpty = currentEntries.isEmpty()

            context.additionalEntries.forEachIndexed { index, entry ->
                if (index != 0 || (!hasTrailingComma && !currentEntriesIsEmpty)) {
                    destructuringDeclaration.addBefore(psiFactory.createComma(), rPar)
                }
                destructuringDeclaration.addBefore(entry, rPar)
            }
        }
    }
}

private fun KaSession.generateNames(
    element: KtDestructuringDeclaration,
    primaryParameters: List<KaValueParameterSymbol>,
): List<String> {
    val nameValidator = KotlinDeclarationNameValidator(
        visibleDeclarationsContext = element,
        checkVisibleDeclarationsContext = true,
        target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
    )

    return primaryParameters.map { parameter ->
        val baseName = parameter.name.asString()
        KotlinNameSuggester.suggestNameByName(baseName) { candidate ->
            nameValidator.validate(candidate)
        }
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.generateTypesIfNeeded(
    element: KtDestructuringDeclaration,
    primaryParameters: List<KaValueParameterSymbol>,
): List<String> {
    if (element.entries.none { it.typeReference != null }) return emptyList()

    return primaryParameters.map {
        it.returnType.render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.IN_VARIANCE,
        )
    }
}
