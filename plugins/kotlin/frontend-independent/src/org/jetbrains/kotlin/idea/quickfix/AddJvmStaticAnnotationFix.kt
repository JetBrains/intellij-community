// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class AddJvmStaticAnnotationFix(declaration: KtCallableDeclaration) : AddAnnotationFix(
    declaration,
    ClassId.topLevel(FqName("kotlin.jvm.JvmStatic")),
    Kind.Declaration(declaration.nameAsSafeName.asString()),
) {

    companion object {
        fun createIfApplicable(element: KtNameReferenceExpression): AddJvmStaticAnnotationFix? {
            val resolved = element.mainReference.resolve() ?: return null
            return if (resolved is KtProperty || resolved is KtNamedFunction) {
                AddJvmStaticAnnotationFix(resolved as KtCallableDeclaration)
            } else {
                null
            }
        }
    }
}
