// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.fqName

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported as _isImported

val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }

@Deprecated(
    "Replace with 'org.jetbrains.kotlin.idea.base.psi.kotlinFqName'",
    replaceWith = ReplaceWith("kotlinFqName", "org.jetbrains.kotlin.idea.base.psi.kotlinFqName"),
)
fun PsiElement.getKotlinFqName(): FqName? = this.kotlinFqName

@ApiStatus.ScheduledForRemoval
@Deprecated(
    "For binary compatibility",
    replaceWith = ReplaceWith("this.isImported(importPath, skipAliasedImports)", "org.jetbrains.kotlin.idea.base.utils.fqname.isImported"),
)
fun FqName.isImported(importPath: ImportPath, skipAliasedImports: Boolean = true): Boolean =
    _isImported(importPath, skipAliasedImports)