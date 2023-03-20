// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("RootPrefixUtils")

package org.jetbrains.kotlin.idea.base.analysis

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes2
import org.jetbrains.kotlin.resolve.QualifiedExpressionResolver

/*
 * See documentation for ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE in 'QualifiedExpressionResolver'.
 */

@ApiStatus.Internal
fun CallableId.asFqNameWithRootPrefixIfNeeded(): FqName {
    return asSingleFqName().withRootPrefixIfNeeded()
}

@ApiStatus.Internal
fun FqName.withRootPrefixIfNeeded(targetElement: KtElement? = null): FqName {
    if (canAddRootPrefix() && targetElement?.canAddRootPrefix() != false) {
        return FqName(QualifiedExpressionResolver.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_WITH_DOT + asString())
    }

    return this
}

@ApiStatus.Internal
fun KtElement.canAddRootPrefix(): Boolean {
    return getParentOfTypes2<KtImportDirective, KtPackageDirective>() == null
}

@ApiStatus.Internal
fun FqName.canAddRootPrefix(): Boolean {
    return !asString().startsWith(QualifiedExpressionResolver.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_WITH_DOT)
            && parentOrNull()?.isRoot == false
}