// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.extractDestructuringComponentNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal object DestructuringRenameFactory {
    val renameToMatchParameterName = KotlinQuickFixFactory.IntentionBased { d: KaFirDiagnostic.DestructuringShortFormNameMismatch -> createFix(d.psi) }

    context(session: KaSession)
    private fun expectedNameForEntry(entry: KtDestructuringDeclarationEntry, declaration: KtDestructuringDeclaration): Pair<Name, List<Name>>? {
        val allExpectedNames = session.extractDestructuringComponentNames(declaration) ?: return null
        val entryIndex = declaration.entries.indexOf(entry).takeIf { it >= 0 } ?: return null
        val targetName = allExpectedNames.getOrNull(entryIndex) ?: return null
        return targetName to allExpectedNames
    }

    context(_: KaSession)
    private fun createFix(psi: PsiElement): List<RenameDestructuringEntriesToMatchPropertiesFix> {
        val entry = psi as? KtDestructuringDeclarationEntry ?: return emptyList()
        val declaration = entry.parent as? KtDestructuringDeclaration ?: return emptyList()
        if (declaration.isFullForm || declaration.hasSquareBrackets() || declaration.entries.isEmpty()) return emptyList()

        val (expected, expectedNames) = expectedNameForEntry(entry, declaration) ?: return emptyList()
        val current = entry.nameAsName ?: return emptyList()
        if (current == expected) return emptyList()

        val mismatchesCount = declaration.entries.zip(expectedNames).count { (entry, expectedName) ->
            entry.nameAsName?.let { it != expectedName } == true
        }

        return listOf(RenameDestructuringEntriesToMatchPropertiesFix(entry, expected, expectedNames, mismatchesCount))
    }

    private class RenameDestructuringEntriesToMatchPropertiesFix(
        entry: KtDestructuringDeclarationEntry,
        private val targetName: Name,
        private val expectedNames: List<Name>,
        private val mismatchesCount: Int
    ) : KotlinQuickFixAction<KtDestructuringDeclarationEntry>(entry), PriorityAction {

        override fun startInWriteAction(): Boolean = false

        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

        override fun getText(): String =
            if (mismatchesCount > 1) {
                KotlinBundle.message("rename.vars.to.match.destructuring.properties")
            } else {
                KotlinBundle.message("rename.var.to.property.name", targetName)
            }

        override fun getFamilyName(): String =
            KotlinBundle.message("rename.var.to.match.destructuring.property")

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val declaration = element?.parent as? KtDestructuringDeclaration ?: return
            val entries = declaration.entries
            if (entries.size != expectedNames.size) return

            val mismatched = entries.mapIndexedNotNull { index, entry ->
                    val name = entry.nameAsName ?: return@mapIndexedNotNull null
                    val expected = expectedNames[index]
                    if (name == expected) null else entry to expected
                }
            if (mismatched.isEmpty()) return

            val (firstElementToRename, firstNameToRename) = mismatched.first()
            val scopes = mutableMapOf<String, MutableSet<KtElement>>()
            scopes[firstNameToRename.asString()] = getContainingBlock(firstElementToRename)?.let { mutableSetOf(it) } ?: mutableSetOf()

            val processor = RenameProcessor(
                project, firstElementToRename, firstNameToRename.asString(), false, false
            )

            mismatched.drop(1).forEach { (entry, name) ->
                val nameStr = name.asString()
                val scope = getContainingBlock(entry) ?: return@forEach
                val namedScopes = scopes.getOrPut(nameStr) { mutableSetOf() }
                if (namedScopes.none { PsiTreeUtil.isAncestor(it, scope, false) } && namedScopes.add(scope)) {
                    processor.addElement(entry, nameStr)
                }
            }

            processor.run()
        }

        private fun getContainingBlock(element: PsiElement): KtElement? = element.parent.parent as? KtElement
    }
}