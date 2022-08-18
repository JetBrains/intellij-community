// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveClassesOrPackages

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler

class KotlinAwareJavaMoveClassesOrPackagesHandler : JavaMoveClassesOrPackagesHandler() {
    override fun createMoveClassesOrPackagesToNewDirectoryDialog(
        directory: PsiDirectory,
        elementsToMove: Array<out PsiElement>,
        moveCallback: MoveCallback?
    ) = KotlinAwareMoveClassesOrPackagesToNewDirectoryDialog(directory, elementsToMove, moveCallback)
}