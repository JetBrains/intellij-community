// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.declarations

import com.intellij.codeInspection.BatchQuickFix
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.siyeh.ig.psiutils.PsiElementOrderComparator
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.convertDestructuringToPositionalForm
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.isPositionalDestructuringType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

private data class DestructuringAnalysisResult(
    /**
     * If this flag is `true`, the positional destructuring form is preferred over the name-based one for the analyzed destructuring declaration.
     *
     * Otherwise, the name-based form should be preferred.
     */
    val preferPositionalDestructuring: Boolean,
    val entriesWithNameMismatch: List<Pair<KtDestructuringDeclarationEntry, Name>>  // entry to expected name
)

internal class DestructingShortFormNameMismatchInspection : AbstractKotlinInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean {
        val languageVersionSettings = file.languageVersionSettings
        return languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring)
                && !languageVersionSettings.supportsFeature(LanguageFeature.DeprecateNameMismatchInShortDestructuringWithParentheses)
                && !languageVersionSettings.supportsFeature(LanguageFeature.EnableNameBasedDestructuringShortForm)
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitDestructuringDeclaration(declaration: KtDestructuringDeclaration) {
            if (declaration.isFullForm || declaration.hasSquareBrackets()) return
            if (declaration.entries.isEmpty()) return

            val analysisResult = analyze(declaration) {
                analyzeDestructuringDeclaration(declaration)
            } ?: return

            if (analysisResult.entriesWithNameMismatch.isEmpty()) return

            if (analysisResult.preferPositionalDestructuring) {
                val highlightRange = ApplicabilityRanges.destructuringDeclarationParens(declaration).singleOrNull() ?: return
                holder.registerProblem(
                    declaration,
                    highlightRange,
                    KotlinBundle.message("inspection.positional.destructuring.migration"),
                    ConvertNameBasedDestructuringShortFormToPositionalFix()
                )
            } else {
                // For regular data classes: offer rename fix on each mismatched entry
                // (full form conversion is available as a separate intention)
                for ((entry, expectedName) in analysisResult.entriesWithNameMismatch) {
                    holder.registerProblem(
                        entry,
                        KotlinBundle.message("inspection.destruction.declaration.mismatch"),
                        RenameVariableToMatchPropertiesQuickFix(expectedName)
                    )
                }
            }
        }
    }
}

private fun KaSession.analyzeDestructuringDeclaration(declaration: KtDestructuringDeclaration): DestructuringAnalysisResult? {
    val constructorParameters = extractPrimaryParameters(declaration) ?: return null

    // Check if the destructured type is intended for positional destructuring (Pair, Triple, IndexedValue)
    val isPositionalDestructuring = declaration.isPositionalDestructuringType()

    // Find all entries with name mismatch
    val entriesWithNameMismatch = declaration.entries.zip(constructorParameters)
        .filter { (entry, param) ->
            val entryName = entry.nameAsName
            entryName != null && entryName != param.name
        }
        .map { (entry, param) -> entry to param.name }

    return DestructuringAnalysisResult(
        preferPositionalDestructuring = isPositionalDestructuring,
        entriesWithNameMismatch = entriesWithNameMismatch
    )
}


private class RenameVariableToMatchPropertiesQuickFix(
    private val targetName: Name
) : LocalQuickFix, BatchQuickFix  {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        RenameProcessor(project, element, targetName.toString(), false, false).run()
    }

    override fun applyFix(
        project: Project,
        descriptors: Array<CommonProblemDescriptor>,
        psiElementsToIgnore: MutableList<PsiElement>,
        refreshViews: Runnable?
    ) {
        val orderComparator = PsiElementOrderComparator.getInstance()
        val elementToName = descriptors.filterIsInstance<ProblemDescriptor>().mapNotNull { d ->
            val element = d.psiElement ?: return@mapNotNull null
            val fix = d.fixes?.filterIsInstance<RenameVariableToMatchPropertiesQuickFix>()?.firstOrNull() ?: return@mapNotNull null
            element to fix.targetName.toString()
        }.sortedWith { p1, p2 -> orderComparator.compare(p1.first, p2.first) }
        if (elementToName.isEmpty()) return

        // these are local variables, and we should not have any additional elements to rename based on new names,
        // so it must be fine to rename in batch as simple entries without the full processor restart.
        // Entries in the same code blocks with the same target names are ignored because it would lead to re-declarations,
        // which won't be caught by the processor
        val firstToRename = elementToName.first()
        val scopes = mutableMapOf<String, MutableSet<KtElement>>()

        fun getContainingBlock(element: PsiElement): KtElement? = element.parent.parent as? KtElement

        val (firstElementToRename, firstNameToRename) = firstToRename
        psiElementsToIgnore.add(firstElementToRename)
        scopes[firstNameToRename] = getContainingBlock(firstElementToRename)?.let { mutableSetOf(it) } ?: mutableSetOf()
        object : RenameProcessor(project, firstElementToRename, firstNameToRename, false, false) {
            init {
                elementToName.subList(1, elementToName.size).forEach { (element, name) ->
                    val scope = getContainingBlock(element)
                    val namedScopes = scopes.getOrPut(name) { mutableSetOf() }
                    if (scope != null && namedScopes.none { PsiTreeUtil.isAncestor(it, scope, false) } && namedScopes.add(scope)) {
                        addElement(element, name)
                        psiElementsToIgnore.add(element)
                    }
                }
            }
            override fun doRun() {
                super.doRun()
                refreshViews?.run()
            }
        }.run()
    }

    override fun getName(): @IntentionName String = KotlinBundle.message("rename.var.to.property.name", targetName)

    override fun startInWriteAction(): Boolean = false
    
    override fun getFamilyName(): String = KotlinBundle.message("rename.var.to.match.destructing.property")
}

private class ConvertNameBasedDestructuringShortFormToPositionalFix : KotlinModCommandQuickFix<KtDestructuringDeclaration>() {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.positional.destructuring.migration.fix")

    override fun applyFix(
        project: Project,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater
    ) {
        convertDestructuringToPositionalForm(element)
    }
}

