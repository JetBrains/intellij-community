// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.navigation

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.kotlin.psi.KtFile

class KotlinScriptUseScopeEnlarger : UseScopeEnlarger() {
    override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
        val file = element.containingFile as? KtFile ?: return null
        return if (file.isScript()) ProjectFileByExtensionSearchScope(element.project, "kts") else null
    }

    private class ProjectFileByExtensionSearchScope(project: Project, private val extension: String) :
        DelegatingGlobalSearchScope(projectScope(project)) {
        override fun contains(file: VirtualFile): Boolean {
            return super.contains(file) && file.extension == extension
        }
    }
}
