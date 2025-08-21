// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.*
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.kotlin.idea.core.script.shared.AbstractScriptDependencySourceNavigationPolicyForJavaClasses
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.util.toPsiFile

class ScriptDependencySourceNavigationPolicyForJavaClasses : AbstractScriptDependencySourceNavigationPolicyForJavaClasses() {
    override fun getNavigationElement(file: ClsFileImpl): PsiElement? {
        val project = file.project

        if (file.virtualFile !in ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope()) return null

        val psiClass = file.classes.firstOrNull() as? ClsClassImpl ?: return null

        val candidates = FilenameIndex.getVirtualFilesByName(
            psiClass.sourceFileName,
            ScriptDependencyAware.getInstance(project).getAllScriptDependenciesSourcesScope()
        )

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