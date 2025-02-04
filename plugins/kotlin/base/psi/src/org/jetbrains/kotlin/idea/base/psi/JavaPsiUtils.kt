// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JavaPsiUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.*
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration

val PsiClass.classIdIfNonLocal: ClassId?
    get() {
        if (this is KtLightClass) {
            return this.kotlinOrigin?.getClassId()
        }
        val packageName = (containingFile as? PsiClassOwner)?.packageName ?: return null
        val packageFqName = FqName(packageName)

        val classesNames = parentsOfType<PsiClass>().map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageFqName, FqName(classesNames.joinToString(separator = ".")), false)
    }

val PsiElement.kotlinFqName: FqName?
    get() = when (val element = namedUnwrappedElement) {
        is PsiPackage -> FqName(element.qualifiedName)
        is PsiClass -> element.qualifiedName?.let(::FqName)
        is PsiMember -> element.getName()?.let { name ->
            val prefix = element.containingClass?.qualifiedName
            FqName(if (prefix != null) "$prefix.$name" else name)
        }
        is KtNamedDeclaration -> element.fqName
        else -> null
    }

fun PsiJavaModule.findRequireDirective(requiredName: String): PsiRequiresStatement? {
    return requires.find { it.moduleName == requiredName }
}