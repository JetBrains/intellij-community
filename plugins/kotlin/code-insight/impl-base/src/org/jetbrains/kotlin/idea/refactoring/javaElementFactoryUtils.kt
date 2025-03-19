// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiReferenceList.Role

fun PsiElementFactory.createReferenceListWithRole(
    references: Array<PsiJavaCodeReferenceElement>,
    role: Role
): PsiReferenceList? {
    val refsText = references.map { it.canonicalText }
    val refListText = if (refsText.isNotEmpty()) refsText.joinToString() else return null
    return when (role) {
        Role.THROWS_LIST -> createMethodFromText("void foo() throws $refListText {}", null).throwsList
        Role.EXTENDS_LIST -> createClassFromText("class Foo extends $refListText {}", null).innerClasses[0].extendsList
        Role.IMPLEMENTS_LIST -> createClassFromText("class Foo implements $refListText {}", null).innerClasses[0].implementsList
        Role.EXTENDS_BOUNDS_LIST -> createTypeParameterFromText("T extends $refListText", null).extendsList
        else -> throw UnsupportedOperationException("Unsupported role $role")
    }
}
