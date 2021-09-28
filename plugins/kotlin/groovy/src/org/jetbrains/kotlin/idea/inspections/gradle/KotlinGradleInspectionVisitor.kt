// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.gradle

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile

@Deprecated(
    "Function moved to a new package.",
    ReplaceWith("getResolvedKotlinGradleVersion(file)", "org.jetbrains.kotlin.idea.groovy.inspections"),
    level = DeprecationLevel.ERROR
)
fun getResolvedKotlinGradleVersion(file: PsiFile): String? {
    return org.jetbrains.kotlin.idea.groovy.inspections.getResolvedKotlinGradleVersion(file)
}

@Deprecated(
    "Function moved to a new package.",
    ReplaceWith("getResolvedKotlinGradleVersion(file)", "org.jetbrains.kotlin.idea.groovy.inspections"),
    level = DeprecationLevel.ERROR
)
fun getResolvedKotlinGradleVersion(module: Module): String? {
    return org.jetbrains.kotlin.idea.groovy.inspections.getResolvedKotlinGradleVersion(module)
}