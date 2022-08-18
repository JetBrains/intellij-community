// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewBuilderProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile

//TODO: workaround for bug in JavaClsStructureViewBuilderProvider, remove when IDEA api is updated
class KtClsStructureViewBuilderProvider : StructureViewBuilderProvider {
    override fun getStructureViewBuilder(fileType: FileType, file: VirtualFile, project: Project): StructureViewBuilder? {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtClsFile ?: return null
        return KotlinStructureViewFactory().getStructureViewBuilder(psiFile)
    }
}
