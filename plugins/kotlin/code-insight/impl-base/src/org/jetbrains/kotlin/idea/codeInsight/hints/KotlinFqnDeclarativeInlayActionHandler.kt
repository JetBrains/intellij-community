// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.module

class KotlinFqnDeclarativeInlayActionHandler : InlayActionHandler {
    companion object {
        const val HANDLER_NAME: String = "kotlin.fqn.class"

        @ApiStatus.Internal
        fun getNavigationElement(from: PsiFile, payload: InlayActionPayload): PsiElement? {
            val fqName = (payload as? StringInlayActionPayload)?.text ?: return null
            val module = from.module ?: return null
            val project = from.project
            val index = ProjectFileIndex.getInstance(project)
            val includeTests = index.isInTestSourceContent(from.virtualFile)
            val scope = module.getModuleWithDependenciesAndLibrariesScope(includeTests)
            return project.resolveClass(fqName, scope)?.navigationElement
        }

    }

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val project = editor.project ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val navigatable = getNavigationElement(psiFile, payload) as? Navigatable
        navigatable?.navigate(true)
    }
}