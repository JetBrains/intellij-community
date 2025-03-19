// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FqNameUtils")
package org.jetbrains.kotlin.idea.base.util

import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.load.java.javaToKotlinNameMap
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.ImportPath

fun FqName.hasIdentifiersOnly(): Boolean = pathSegments().all { it.asString().quoteIfNeeded().isIdentifier() }

fun FqName.quoteIfNeeded(): FqName {
    return FqName(pathSegments().joinToString(".") { it.asString().quoteIfNeeded() })
}

fun FqName.isImported(importPath: ImportPath, skipAliasedImports: Boolean = true): Boolean {
    return when {
        skipAliasedImports && importPath.hasAlias() -> false
        importPath.isAllUnder && !isRoot -> importPath.fqName == this.parent()
        else -> importPath.fqName == this
    }
}

fun ImportPath.isImported(alreadyImported: ImportPath): Boolean {
    return if (isAllUnder || hasAlias()) this == alreadyImported else fqName.isImported(alreadyImported)
}

private fun ImportPath.isImported(imports: Iterable<ImportPath>): Boolean = imports.any { isImported(it) }

fun ImportPath.isImported(imports: Iterable<ImportPath>, excludedFqNames: Iterable<FqName>): Boolean {
    return isImported(imports) && (isAllUnder || this.fqName !in excludedFqNames)
}

fun FqName.isJavaClassNotToBeUsedInKotlin(): Boolean =
    JavaToKotlinClassMap.isJavaPlatformClass(this)
            || this in javaToKotlinNameMap