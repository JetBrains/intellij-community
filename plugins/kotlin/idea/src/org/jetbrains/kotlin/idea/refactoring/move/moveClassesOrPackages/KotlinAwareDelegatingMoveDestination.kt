// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveClassesOrPackages

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.MoveDestination
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.refactoring.move.createMoveUsageInfoIfPossible
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.KotlinDirectoryMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.analyzeConflictsInFile
import org.jetbrains.kotlin.idea.stubindex.KotlinExactPackagesIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KotlinAwareDelegatingMoveDestination(
    private val delegate: MoveDestination,
    private val targetPackage: PsiPackage?,
    private val targetDirectory: PsiDirectory?
) : MoveDestination by delegate {
    override fun analyzeModuleConflicts(
        elements: Collection<PsiElement>,
        conflicts: MultiMap<PsiElement, String>,
        usages: Array<out UsageInfo>
    ) {
        delegate.analyzeModuleConflicts(elements, conflicts, usages)

        if (targetPackage == null || targetDirectory == null) return

        val project = targetDirectory.project
        val moveTarget = KotlinDirectoryMoveTarget(FqName(targetPackage.qualifiedName), targetDirectory.virtualFile)
        val directoriesToMove = elements.flatMapTo(LinkedHashSet<PsiDirectory>()) {
            (it as? PsiPackage)?.directories?.toList() ?: emptyList()
        }
        val projectScope = project.projectScope()
        val filesToProcess = elements.flatMapTo(LinkedHashSet<KtFile>()) {
            if (it is PsiPackage) KotlinExactPackagesIndex.get(it.qualifiedName, project, projectScope) else emptyList()
        }

        val extraElementsForReferenceSearch = LinkedHashSet<PsiElement>()
        val extraElementCollector = object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtNamedDeclaration && element.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                    element.parentsWithSelf.lastOrNull { it is KtNamedDeclaration }?.let { extraElementsForReferenceSearch += it }
                    stopWalking()
                }
                super.visitElement(element)
            }
        }
        filesToProcess.flatMap { it.declarations }.forEach { it.accept(extraElementCollector) }

        val progressIndicator = ProgressManager.getInstance().progressIndicator!!
        progressIndicator.pushState()

        val extraUsages = ArrayList<UsageInfo>()
        try {
            progressIndicator.text = KotlinBundle.message("text.looking.for.usages")
            for ((index, element) in extraElementsForReferenceSearch.withIndex()) {
                progressIndicator.fraction = (index + 1) / extraElementsForReferenceSearch.size.toDouble()
                ReferencesSearch.search(element, projectScope).mapNotNullTo(extraUsages) { ref ->
                    createMoveUsageInfoIfPossible(ref, element, addImportToOriginalFile = true, isInternal = false)
                }
            }
        } finally {
            progressIndicator.popState()
        }

        filesToProcess.forEach {
            analyzeConflictsInFile(it, extraUsages, moveTarget, directoriesToMove, conflicts) {}
        }
    }
}