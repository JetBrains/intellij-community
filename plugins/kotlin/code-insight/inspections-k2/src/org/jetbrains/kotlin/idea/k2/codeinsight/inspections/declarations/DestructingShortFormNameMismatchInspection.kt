// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid


private data class DestructuringEntryInfo(
    val originalPropertyName: Name,
    val needsRename: Boolean
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
        override fun visitDestructuringDeclarationEntry(entry: KtDestructuringDeclarationEntry) {
            if (!isApplicableByPsi(entry)) return

            val entryInfo = analyze(entry) {
                prepareDestructuringEntryInfo(entry) ?: return
            }

            if (!entryInfo.needsRename) return
            holder.registerProblem(
                entry,
                KotlinBundle.message("inspection.destruction.declaration.mismatch"),
                IntentionWrapper(RenameVariableToMatchPropertiesQuickFix(entry, entryInfo.originalPropertyName))
            )
        }
    }

    private fun isApplicableByPsi(element: KtDestructuringDeclarationEntry): Boolean {
        val destructuringDeclaration = element.parent as? KtDestructuringDeclaration ?: return false
        if (destructuringDeclaration.isFullForm) return false
        return element.nameAsName != null
    }
}


private fun KaSession.prepareDestructuringEntryInfo(element: KtDestructuringDeclarationEntry): DestructuringEntryInfo? {
    // Get the parent destructuring declaration
    val destructuringDeclaration = element.parent as? KtDestructuringDeclaration ?: return null
    val constructorParameters = extractPrimaryParameters(destructuringDeclaration) ?: return null
    
    // Find the parameter that corresponds to this entry
    val entryIndex = destructuringDeclaration.entries.indexOf(element)
    if (entryIndex < 0 || entryIndex >= constructorParameters.size) return null

    val param = constructorParameters[entryIndex]
    val entryName = element.nameAsName ?: return null
    val originalPropertyName = param.name
    val needsRename = entryName != originalPropertyName

    return DestructuringEntryInfo(
        originalPropertyName = originalPropertyName,
        needsRename = needsRename
    )
}


private class RenameVariableToMatchPropertiesQuickFix(
    entry: KtDestructuringDeclarationEntry,
    private val targetName: Name
) : KotlinQuickFixAction<KtDestructuringDeclarationEntry>(entry) {
    override fun getText() = KotlinBundle.message("rename.var.to.property.name", targetName)

    override fun startInWriteAction(): Boolean = false
    
    override fun getFamilyName(): String = KotlinBundle.message("rename.var.to.match.destructing.property")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        RenameProcessor(project, element, targetName.toString(), false, false).run()
    }
}

