// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.util.isFileInRoots
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

@ApiStatus.Internal
object KotlinSingleClassFileAnalyzer {
    @JvmStatic
    fun isSingleClassFile(file: KtFile): Boolean = getSingleClass(file) != null

    @JvmStatic
    fun getSingleClass(file: KtFile): KtClassOrObject? {
        // no reason to show a difference between single class and kotlin file for non-source roots kotlin files
        // in consistence with [org.jetbrains.kotlin.idea.projectView.KotlinSelectInProjectViewProvider#getTopLevelElement]
        if (!file.project.isFileInRoots(file.virtualFile)){
            return null
        }

        var targetDeclaration: KtDeclaration? = null
        for (declaration: KtDeclaration in file.declarations) {
            if (!declaration.isPrivate() && declaration !is KtTypeAlias) {
                if (targetDeclaration != null) return null
                targetDeclaration = declaration
            }
        }
        return targetDeclaration?.takeIf { it is KtClassOrObject && StringUtil.getPackageName(file.name) == it.name } as? KtClassOrObject
    }
}