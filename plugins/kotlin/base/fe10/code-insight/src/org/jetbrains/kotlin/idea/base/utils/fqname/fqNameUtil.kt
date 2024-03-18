// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.utils.fqname

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType

/**
 * Returns FqName for given declaration (either Java or Kotlin)
 */
@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.kotlinFqName' instead",
    ReplaceWith("this.kotlinFqName", imports = ["org.jetbrains.kotlin.idea.base.psi.kotlinFqName"])
)
fun PsiElement.getKotlinFqName(): FqName? = when (val element = namedUnwrappedElement) {
    is PsiPackage -> FqName(element.qualifiedName)
    is PsiClass -> element.qualifiedName?.let(::FqName)
    is PsiMember -> element.getName()?.let { name ->
        val prefix = element.containingClass?.qualifiedName
        FqName(if (prefix != null) "$prefix.$name" else name)
    }
    is KtNamedDeclaration -> element.fqName
    else -> null
}

val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }