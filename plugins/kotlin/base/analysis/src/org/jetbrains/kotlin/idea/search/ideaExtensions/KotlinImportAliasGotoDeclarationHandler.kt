// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

class KotlinImportAliasGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val importAlias = sourceElement?.parent as? KtImportAlias ?: return null

        val result = runReadAction {
            importAlias.importDirective?.importedReference?.getQualifiedElementSelector()?.mainReference?.multiResolve(false)
        } ?: return null

        return result.mapNotNull { it.element }.toTypedArray()
    }
}