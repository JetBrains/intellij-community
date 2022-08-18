// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui.KotlinExtractInterfaceDialog
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

object KotlinExtractInterfaceHandler : KotlinExtractSuperHandlerBase(true) {
    val REFACTORING_NAME
        @Nls
        get() = KotlinBundle.message("name.extract.interface")

    override fun getErrorMessage(klass: KtClassOrObject): String? {
        val superMessage = super.getErrorMessage(klass)
        if (superMessage != null) return superMessage
        if (klass is KtClass && klass.isAnnotation()) return KotlinBundle.message("error.text.interface.cannot.be.extracted.from.an.annotation.class")
        return null
    }

    override fun createDialog(klass: KtClassOrObject, targetParent: PsiElement) =
        KotlinExtractInterfaceDialog(
            originalClass = klass,
            targetParent = targetParent,
            conflictChecker = { checkConflicts(klass, it) },
            refactoring = { ExtractSuperRefactoring(it).performRefactoring() }
        )
}