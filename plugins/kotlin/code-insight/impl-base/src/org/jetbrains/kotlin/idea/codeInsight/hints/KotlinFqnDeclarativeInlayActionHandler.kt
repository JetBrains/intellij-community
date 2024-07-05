// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.util.module

class KotlinFqnDeclarativeInlayActionHandler : InlayActionHandler {
    companion object {
        const val HANDLER_NAME: String = "kotlin.fqn.class"
    }

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val project = editor.project ?: return
        val fqName = (payload as? StringInlayActionPayload)?.text ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val scope = psiFile?.module?.moduleContentWithDependenciesScope ?: GlobalSearchScope.allScope(project)
        val navigatable = project.resolveClass(fqName, scope)?.navigationElement as? Navigatable
        navigatable?.navigate(true)
    }
}