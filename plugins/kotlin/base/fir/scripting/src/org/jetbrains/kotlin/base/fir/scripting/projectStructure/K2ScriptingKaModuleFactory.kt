// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptModuleImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.fir.projectStructure.K2KaModuleFactory
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.psi.KtFile

internal class K2ScriptingKaModuleFactory : K2KaModuleFactory {
    override fun createKaModuleByPsiFile(file: PsiFile): KaModule? {
        val ktFile = file as? KtFile ?: return null

        if (file.virtualFile.extension == KotlinFileType.EXTENSION) {
            /*
            We cannot be 100% sure that a file is a script solely based on its extension.
            Details explaining why are written in the comments of KTIJ-32922.
            However, this is a workaround to minimize the consequences of KTIJ-32912, which should work in most cases.

            This way, we will not call `ktFile.isScript()` for regular `.kt` files,
            minimizing stub access, which was the cause for KTIJ-32912.
            */
            return null
        }

        if (ktFile.isScript()) {
            val project = file.project
            val virtualFile = file.originalFile.virtualFile
            return KaScriptModuleImpl(project, virtualFile)
        }

        return null
    }

    override fun createSpecialLibraryModule(libraryEntity: LibraryEntity, project: Project): KaLibraryModule? {
        if (libraryEntity.entitySource is KotlinScriptEntitySource) {
            return KaScriptDependencyLibraryModuleImpl(libraryEntity.symbolicId, project)
        }
        return null
    }
}