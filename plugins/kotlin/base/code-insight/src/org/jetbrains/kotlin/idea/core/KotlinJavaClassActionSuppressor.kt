// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import com.intellij.ide.actions.JavaClassActionSuppressor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.base.util.KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.facet.KotlinFacet

internal class KotlinJavaClassActionSuppressor: JavaClassActionSuppressor {
    override fun isSuppressed(dataContext: DataContext): Boolean {
        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex

        return ideView.directories.any { directory: PsiDirectory ->
            val virtualFile = directory.virtualFile
            val module = projectFileIndex.getModuleForFile(virtualFile) ?: return@any false
            val kotlinFacet = KotlinFacet.get(module)
            val kotlinSourceFolders = kotlinFacet?.configuration?.settings?.pureKotlinSourceFolders
            val kotlinSourceRootOfType =
                projectFileIndex.isUnderSourceRootOfType(virtualFile, KOTLIN_SOURCE_ROOT_TYPES) ||
                        (kotlinSourceFolders?.let { virtualFile.toNioPath().toString() in it } ?: false)
            kotlinSourceRootOfType
        }
    }
}
