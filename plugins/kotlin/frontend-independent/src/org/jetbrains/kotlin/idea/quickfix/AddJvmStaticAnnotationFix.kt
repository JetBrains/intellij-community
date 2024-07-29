// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class AddJvmStaticAnnotationFix(declaration: KtCallableDeclaration) : AddAnnotationFix(
    declaration,
    ClassId.topLevel(FqName("kotlin.jvm.JvmStatic")),
    Kind.Declaration(declaration.nameAsSafeName.asString())
) {
    override fun getFamilyName(): String = text
}
