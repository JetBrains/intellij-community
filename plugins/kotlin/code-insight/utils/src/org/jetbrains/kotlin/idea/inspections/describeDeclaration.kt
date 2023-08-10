// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter

/**
 * @return string description of declaration, like `Function "describe"`
 */
@Nls
fun KtNamedDeclaration.describe(): String? = when (this) {
    is KtClass -> "${if (isInterface()) KotlinBundle.message("interface") else KotlinBundle.message("class")} \"$name\""
    is KtObjectDeclaration -> KotlinBundle.message("object.0", name.toString())
    is KtNamedFunction -> KotlinBundle.message("function.01", name.toString())
    is KtSecondaryConstructor -> KotlinBundle.message("constructor")
    is KtProperty -> KotlinBundle.message("property.0", name.toString())
    is KtParameter -> if (this.isPropertyParameter())
        KotlinBundle.message("property.0", name.toString())
    else
        KotlinBundle.message("parameter.0", name.toString())
    is KtTypeParameter -> KotlinBundle.message("type.parameter.0", name.toString())
    is KtTypeAlias -> KotlinBundle.message("type.alias.0", name.toString())
    else -> null
}