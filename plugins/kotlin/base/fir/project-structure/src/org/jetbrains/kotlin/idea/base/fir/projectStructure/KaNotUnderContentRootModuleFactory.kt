// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule

interface KaNotUnderContentRootModuleFactory {
    fun create(project: Project, file: PsiFile?): KaNotUnderContentRootModule?
    companion object {
        val EP_NAME: ExtensionPointName<KaNotUnderContentRootModuleFactory> =
            ExtensionPointName.create("org.jetbrains.kotlin.k2NotUnderContentRootModuleFactory")
    }
}
