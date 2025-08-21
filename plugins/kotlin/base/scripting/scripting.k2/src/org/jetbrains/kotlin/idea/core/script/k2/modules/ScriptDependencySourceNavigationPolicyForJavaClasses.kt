// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.NonClasspathDirectoriesScope.compose
import org.jetbrains.kotlin.idea.core.script.shared.AbstractScriptDependencySourceNavigationPolicyForJavaClasses
import org.jetbrains.kotlin.idea.core.util.toPsiFile

class ScriptDependencySourceNavigationPolicyForJavaClasses : AbstractScriptDependencySourceNavigationPolicyForJavaClasses() {

    override fun getNavigationElement(file: ClsFileImpl): PsiElement? {
        val project = file.project

        val psiClass = file.classes.firstOrNull() as? ClsClassImpl ?: return null

        val virtualFile = file.getContainingFile().getVirtualFile()
        val jar = ProjectFileIndex.getInstance(project).getClassRootForFile(virtualFile) ?: return null

        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val snapshot = project.workspaceModel.currentSnapshot
        val index = snapshot.getVirtualFileUrlIndex()

        val sources = index.findEntitiesByUrl(jar.toVirtualFileUrl(urlManager))
            .filterIsInstance<KotlinScriptLibraryEntity>()
            .flatMap { it.sources }.toSet().mapNotNull { it.virtualFile }
        if (sources.none()) return null

        val candidates = FilenameIndex.getVirtualFilesByName(psiClass.sourceFileName, compose(sources))
        for (virtualFile in candidates) {
            if (!virtualFile.isValid) continue

            val sourceFile = virtualFile.toPsiFile(project)
            if (sourceFile != null && sourceFile.isValid && sourceFile is PsiClassOwner) {
                if (sourceFile.classes.any { it.isEquivalentTo(psiClass) }) {
                    return sourceFile
                }
            }
        }

        return null
    }
}