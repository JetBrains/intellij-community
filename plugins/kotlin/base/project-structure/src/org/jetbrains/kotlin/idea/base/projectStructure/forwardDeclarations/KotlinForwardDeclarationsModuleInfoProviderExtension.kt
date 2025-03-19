// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProviderExtension
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.register

/**
 * An extension to provide the correct [IdeaModuleInfo] for synthetic forward declaration files.
 *
 * Generated files don't belong to the project and need to be mapped to the corresponding library explicitly.
 * Results are tracked per-project by the [KotlinForwardDeclarationsFileOwnerTracker].
 */
internal class KotlinForwardDeclarationsModuleInfoProviderExtension : ModuleInfoProviderExtension {
    override suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(
        element: PsiElement,
        file: PsiFile,
        virtualFile: VirtualFile
    ) {
        registerByVirtualFile(file.project, virtualFile)
    }

    override suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(
        project: Project,
        virtualFile: VirtualFile,
        isLibrarySource: Boolean,
        config: ModuleInfoProvider.Configuration
    ) {
        registerByVirtualFile(project, virtualFile)
    }

    // Kotlin/Native forward declarations can't belong to a module
    override suspend fun SequenceScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile) {}

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.registerByVirtualFile(project: Project, virtualFile: VirtualFile) {
        if (!virtualFile.isValid) return
        KotlinForwardDeclarationsFileOwnerTracker.getInstance(project).getFileOwner(virtualFile)?.let { kaModule ->
            register(kaModule.moduleInfo)
        }
    }
}
