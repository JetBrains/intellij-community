// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move.moveClassesOrPackages

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler

class KotlinAwareJavaMovePackagesHandler : JavaMoveClassesOrPackagesHandler() {
    override fun createMoveClassesOrPackagesToNewDirectoryDialog(
        directory: PsiDirectory,
        elementsToMove: Array<PsiElement>,
        moveCallback: MoveCallback?
    ) = KotlinAwareMovePackagesToNewDirectoryDialog(directory, elementsToMove, moveCallback)

    override fun canMove(elements: Array<PsiElement>, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
        return elements.isNotEmpty() && elements.all(::isPackageOrDirectory)
    }

    override fun isValidTarget(psiElement: PsiElement?, sources: Array<PsiElement>): Boolean = isPackageOrDirectory(psiElement)

    override fun tryToMove(
        element: PsiElement?,
        project: Project?,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?,
    ): Boolean = false
}