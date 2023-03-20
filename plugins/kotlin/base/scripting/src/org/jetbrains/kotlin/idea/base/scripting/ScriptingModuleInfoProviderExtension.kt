// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProviderExtension
import org.jetbrains.kotlin.idea.base.projectStructure.register
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.base.projectStructure.isKotlinBinary
import org.jetbrains.kotlin.idea.core.script.ucache.getAllScriptDependenciesSourcesScope
import org.jetbrains.kotlin.idea.core.script.ucache.getAllScriptsDependenciesClassFilesScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal class ScriptingModuleInfoProviderExtension : ModuleInfoProviderExtension {
    override suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(
        element: PsiElement,
        file: PsiFile,
        virtualFile: VirtualFile
    ) {
        val ktFile = file as? KtFile ?: return
        val isScript = runReadAction { ktFile.isValid && ktFile.isScript() }

        if (isScript) {
            val scriptDefinition = ktFile.findScriptDefinition()
            if (scriptDefinition != null) {
                register(ScriptModuleInfo(element.project, virtualFile, scriptDefinition))
            }
        }
    }

    override suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(
        project: Project,
        virtualFile: VirtualFile,
        isLibrarySource: Boolean
    ) {
        val isBinary = virtualFile.fileType.isKotlinBinary

        if (isBinary && virtualFile in getAllScriptsDependenciesClassFilesScope(project)) {
            if (isLibrarySource) {
                register(ScriptDependenciesSourceInfo.ForProject(project))
            } else {
                register(ScriptDependenciesInfo.ForProject(project))
            }
        }

        if (!isBinary && virtualFile in getAllScriptDependenciesSourcesScope(project)) {
            register(ScriptDependenciesSourceInfo.ForProject(project))
        }
    }

    override suspend fun SequenceScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile) {
        if (ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType) {
            val scratchModuleName = ScriptRelatedModuleNameFile[project, virtualFile]
            if (scratchModuleName != null) {
                val moduleManager = ModuleManager.getInstance(project)
                yieldIfNotNull(moduleManager.findModuleByName(scratchModuleName))
            }
        }
    }
}