// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.fqName

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName as _getKotlinFqName
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported as _isImported

val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }

@Deprecated(
    "For binary compatibility",
    replaceWith = ReplaceWith("getKotlinFqName()", "org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName"),
)
fun PsiElement.getKotlinFqName(): FqName? =
    _getKotlinFqName()

@Deprecated(
    "For binary compatibility",
    replaceWith = ReplaceWith("isImported", "org.jetbrains.kotlin.idea.base.utils.fqname.isImported"),
)
fun FqName.isImported(importPath: ImportPath, skipAliasedImports: Boolean = true): Boolean =
    _isImported(importPath, skipAliasedImports)