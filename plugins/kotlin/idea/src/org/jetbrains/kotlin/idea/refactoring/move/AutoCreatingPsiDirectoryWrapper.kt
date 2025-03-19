// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.MoveDestination

sealed class AutoCreatingPsiDirectoryWrapper {
    class ByPsiDirectory(private val psiDirectory: PsiDirectory) : AutoCreatingPsiDirectoryWrapper() {
        override fun getPackageName(): String = JavaDirectoryService.getInstance()!!.getPackage(psiDirectory)?.qualifiedName ?: ""
        override fun getOrCreateDirectory(source: PsiDirectory) = psiDirectory
    }

    class ByMoveDestination(private val moveDestination: MoveDestination) : AutoCreatingPsiDirectoryWrapper() {
        override fun getPackageName() = moveDestination.targetPackage.qualifiedName
        override fun getOrCreateDirectory(source: PsiDirectory): PsiDirectory = moveDestination.getTargetDirectory(source)
    }

    abstract fun getPackageName(): String
    abstract fun getOrCreateDirectory(source: PsiDirectory): PsiDirectory
}

fun MoveDestination.toDirectoryWrapper() = AutoCreatingPsiDirectoryWrapper.ByMoveDestination(this)
fun PsiDirectory.toDirectoryWrapper() = AutoCreatingPsiDirectoryWrapper.ByPsiDirectory(this)