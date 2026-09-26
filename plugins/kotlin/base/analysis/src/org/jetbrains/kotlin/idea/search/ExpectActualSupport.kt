// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtDeclaration

interface ExpectActualSupport {
    fun expectDeclarationIfAny(declaration: KtDeclaration): KtDeclaration?
    fun actualsForExpect(declaration: KtDeclaration, module: Module? = null): Set<KtDeclaration>

    companion object {
        fun getInstance(project: Project): ExpectActualSupport = project.service()
    }
}