// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinPsiUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil

val KtClassOrObject.classIdIfNonLocal: ClassId?
    get() {
        if (KtPsiUtil.isLocal(this)) return null
        val packageName = containingKtFile.packageFqName
        val classesNames = parentsOfType<KtDeclaration>().map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageName, FqName(classesNames.joinToString(separator = ".")), /*local=*/false)
    }