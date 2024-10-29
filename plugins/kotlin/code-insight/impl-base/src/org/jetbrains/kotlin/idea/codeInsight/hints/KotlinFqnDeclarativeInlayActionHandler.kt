// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.base.util.module

class KotlinFqnDeclarativeInlayActionHandler : InlayActionHandler {
    companion object {
        const val HANDLER_NAME: String = "kotlin.fqn.class"
    }

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val project = editor.project ?: return
        val fqName = (payload as? StringInlayActionPayload)?.text ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val module = psiFile?.module ?: return
        val index = ProjectFileIndex.getInstance(project)
        val includeTests = index.isInTestSourceContent(psiFile.virtualFile)
        val scope = module.getModuleWithDependenciesAndLibrariesScope(includeTests)
        val navigatable = project.resolveClass(fqName, scope)?.navigationElement as? Navigatable
        navigatable?.navigate(true)
    }
}