// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource.KaLibrarySourceModuleBase
import org.jetbrains.kotlin.psi.KtFile

internal class KaScriptDependencyLibrarySourceModuleImpl(
    override val binaryLibrary: KaScriptDependencyLibraryModuleImpl,
) : KaLibrarySourceModuleBase(), KaScriptDependencyModule {
    override val file: KtFile? get() = null

    override val sourceRoots: Array<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        binaryLibrary.entity.sources.mapNotNull { it.virtualFile }.toTypedArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaScriptDependencyLibrarySourceModuleImpl
                && binaryLibrary == other.binaryLibrary
    }

    override fun hashCode(): Int {
        return binaryLibrary.hashCode()
    }
}