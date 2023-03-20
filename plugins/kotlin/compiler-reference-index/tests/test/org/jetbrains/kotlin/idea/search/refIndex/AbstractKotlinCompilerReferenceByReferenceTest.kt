// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.PsiElement

abstract class AbstractKotlinCompilerReferenceByReferenceTest : AbstractKotlinCompilerReferenceTest() {
    override fun findDeclarationAtCaret(): PsiElement {
        return myFixture.getReferenceAtCaretPosition()?.resolve() ?: error("declaration at caret not found")
    }
}
