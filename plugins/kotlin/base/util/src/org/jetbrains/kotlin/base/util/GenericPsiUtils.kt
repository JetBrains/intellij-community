// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GenericPsiUtils")
package org.jetbrains.kotlin.base.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)