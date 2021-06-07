// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

@Service(Service.Level.PROJECT)
class KotlinCompilerReferenceIndexService(project: Project) {
    fun getScopeWithCodeReferences(element: PsiElement): GlobalSearchScope? = null

    companion object {
        operator fun get(project: Project): KotlinCompilerReferenceIndexService = project.service()
        fun getInstanceIfEnable(project: Project): KotlinCompilerReferenceIndexService? = if (isEnabled) get(project) else null
        val isEnabled: Boolean get() = CompilerReferenceService.isEnabled()
    }
}