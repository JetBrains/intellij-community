// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import javax.swing.Icon

class PsiClassLookupObject(val psiClass: PsiClass) : DeclarationLookupObjectImpl(null) {
    override val psiElement: PsiElement
        get() = psiClass

    override fun getIcon(flags: Int): Icon = psiClass.getIcon(flags)
}