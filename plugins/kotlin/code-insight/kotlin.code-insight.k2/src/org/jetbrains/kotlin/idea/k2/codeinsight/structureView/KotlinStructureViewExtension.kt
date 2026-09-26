// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

interface KotlinStructureViewExtension {
    fun isApplicable(file: KtFile): Boolean

    fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder

    companion object {
        val EP_NAME: ExtensionPointName<KotlinStructureViewExtension> =
            ExtensionPointName.create("org.jetbrains.kotlin.structureViewExtension")
    }
}
