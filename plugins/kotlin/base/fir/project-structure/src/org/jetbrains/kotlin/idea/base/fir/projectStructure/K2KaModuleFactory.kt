// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

/**
 * Allows customizing the behavior of [org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProvider].
 */
@ApiStatus.Internal
interface K2KaModuleFactory {
    /**
     * Allows creating an additional candidate module for creating [KaModule] by a given [PsiFile].
     *
     * Needed to override default behavior.
     */
    fun createKaModuleByPsiFile(file: PsiFile): KaModule? = null

    /**
     * Allows overriding the behavior of how we create [KaLibraryModule] by some [LibraryEntity].
     *
     * If a non-null value is returned, it will be used instead of the default one.
     */
    fun createSpecialLibraryModule(libraryEntity: LibraryEntity, project: Project): KaLibraryModule? = null

    companion object {
        val EP_NAME: ExtensionPointName<K2KaModuleFactory> = ExtensionPointName<K2KaModuleFactory>("org.jetbrains.kotlin.k2KaModuleFactory")
    }
}
