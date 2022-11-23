// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal class K1RenameRefactoringSupport : KotlinRenameRefactoringSupport {
    override fun processForeignUsages(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        fallbackHandler: (UsageInfo) -> Unit
    ) {
        ForeignUsagesRenameProcessor.processAll(element, newName, usages, fallbackHandler)
    }

    override fun prepareForeignUsagesRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        ForeignUsagesRenameProcessor.prepareRenaming(element, newName, allRenames, scope)
    }

    override fun checkRedeclarations(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        org.jetbrains.kotlin.idea.refactoring.rename.checkRedeclarations(declaration, newName, result)
    }

    override fun checkOriginalUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    ) {
        org.jetbrains.kotlin.idea.refactoring.rename.checkOriginalUsagesRetargeting(declaration, newName, originalUsages, newUsages)
    }

    override fun checkNewNameUsagesRetargeting(declaration: KtNamedDeclaration, newName: String, newUsages: MutableList<UsageInfo>) {
        org.jetbrains.kotlin.idea.refactoring.rename.checkNewNameUsagesRetargeting(declaration, newName, newUsages)
    }

    override fun checkAccidentalPropertyOverrides(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        org.jetbrains.kotlin.idea.refactoring.rename.checkAccidentalPropertyOverrides(declaration, newName, result)
    }
}