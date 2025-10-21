// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.util.isFileInRoots
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.stubs.elements.KtTokenSets

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

        /**
         * Returns true if more iterations are needed.
         *
         * [targetDeclaration] points to the only one non-private declaration, otherwise it is null.
         */
        fun handleDeclaration(psiElement: PsiElement?): Boolean {
            val declaration = psiElement as? KtDeclaration ?: return true
            if (!declaration.isPrivate() && declaration !is KtTypeAlias) {
                if (targetDeclaration != null) {
                    targetDeclaration = null
                    return false
                }
                targetDeclaration = declaration
            }
            return true
        }

        // do not build AST for stubs when it is unnecessary.
        file.withGreenStubOrAst(
            { fileStub ->
                for (stubElement in fileStub.childrenStubs) {
                    val elementType = stubElement.elementType
                    if (elementType != KtNodeTypes.TYPEALIAS && elementType in KtTokenSets.DECLARATION_TYPES) {
                        if (!handleDeclaration(stubElement.psi)) return@withGreenStubOrAst
                    }
                }
            }, { fileElement ->
                for (node in fileElement.children()) {
                    if (!handleDeclaration(node.psi)) return@withGreenStubOrAst
                }
            }
        )
        return targetDeclaration?.takeIf { it is KtClassOrObject && StringUtil.getPackageName(file.name) == it.name } as? KtClassOrObject
    }
}