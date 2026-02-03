// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptModuleImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.deserialization.DOT_METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol

internal class FirKaScriptingModuleFactory : FirKaModuleFactory {
    override fun createScriptLibraryModule(
        project: Project,
        entity: KotlinScriptLibraryEntity
    ): KaLibraryModule {
        return KaScriptDependencyLibraryModuleImpl(entity, project)
    }

    override fun createKaModuleByPsiFile(file: PsiFile): KaModule? {
        val ktFile = file as? KtFile ?: return null
        if (file.virtualFile is VirtualFileWindow) return null

        val nameSequence = file.virtualFile.nameSequence
        if (
            nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)
            || nameSequence.endsWith(BuiltInSerializerProtocol.DOT_DEFAULT_EXTENSION)
            || nameSequence.endsWith(DOT_METADATA_FILE_EXTENSION)
        ) {
            /*
            We cannot be 100% sure that a file is a script solely based on its extension.
            Details explaining why are written in the comments of KTIJ-32922.
            However, this is a workaround to minimize the consequences of KTIJ-32912, which should work in most cases.

            This way, we will not call `ktFile.isScript()` for regular `.kt` files,
            minimizing stub access, which was the cause for KTIJ-32912.
            */
            return null
        }

        if (ktFile.isCompiled) {
            /*
            For compiled scripts we should not create any KaScriptModule
            as we do not treat them as scripts, a proper module for them
            should be KaScriptDependencyModule.
            */
            return null
        }

        val project = ktFile.project
        val virtualFile = file.originalFile.virtualFile

        val snapshot = project.workspaceModel.currentSnapshot
        if (!nameSequence.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION)) {
            val workspaceModel = WorkspaceModel.getInstance(project)

            val url = virtualFile.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
            val entitiesByUrl = snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(url)
            if (entitiesByUrl.none { it is KotlinScriptEntity }) {
                return null
            }
        }

        return KaScriptModuleImpl(project, virtualFile, snapshot)
    }
}