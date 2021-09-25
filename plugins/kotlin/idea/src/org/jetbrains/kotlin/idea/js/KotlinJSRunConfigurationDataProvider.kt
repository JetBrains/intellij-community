// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.js

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement

interface KotlinJSRunConfigurationData {
    val element: PsiElement
    val module: Module
    val jsOutputFilePath: String
}

interface KotlinJSRunConfigurationDataProvider<out T : Any> {
    val isForTests: Boolean
    fun getConfigurationData(context: ConfigurationContext): T?
}