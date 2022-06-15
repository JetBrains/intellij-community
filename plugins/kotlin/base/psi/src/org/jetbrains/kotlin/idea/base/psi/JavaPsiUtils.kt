// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JavaPsiUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

val PsiClass.classIdIfNonLocal: ClassId?
    get() {
        if (this is KtLightClass) {
            return this.kotlinOrigin?.getClassId()
        }
        val packageName = (containingFile as? PsiJavaFile)?.packageName ?: return null
        val packageFqName = FqName(packageName)

        val classesNames = parentsOfType<PsiClass>().map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageFqName, FqName(classesNames.joinToString(separator = ".")), false)
    }