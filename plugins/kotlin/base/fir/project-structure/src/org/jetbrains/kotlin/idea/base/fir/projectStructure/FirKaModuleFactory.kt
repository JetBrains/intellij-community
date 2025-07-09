// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.idea.KotlinScriptEntity
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity

/**
 * Allows customizing the behavior of [org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProvider].
 */
@ApiStatus.Internal
interface FirKaModuleFactory {
    fun createScriptModule(project: Project, entity: KotlinScriptEntity): KaScriptModule? = null
    fun createScriptLibraryModule(project: Project, entity: KotlinScriptLibraryEntity): KaLibraryModule? = null
    fun createKaModuleByPsiFile(file: PsiFile): KaModule? = null

    companion object {
        val EP_NAME: ExtensionPointName<FirKaModuleFactory> = ExtensionPointName<FirKaModuleFactory>("org.jetbrains.kotlin.k2KaModuleFactory")
    }
}
