// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GenericPsiUtils")
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.ApiStatus

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

@ApiStatus.Internal
fun PsiElement.reformat(canChangeWhiteSpacesOnly: Boolean = false){
    CodeStyleManager.getInstance(project).reformat(this, canChangeWhiteSpacesOnly)
}

@ApiStatus.Internal
fun PsiElement.reformatted(canChangeWhiteSpacesOnly: Boolean = false): PsiElement {
    reformat(canChangeWhiteSpacesOnly = canChangeWhiteSpacesOnly)
    return this
}