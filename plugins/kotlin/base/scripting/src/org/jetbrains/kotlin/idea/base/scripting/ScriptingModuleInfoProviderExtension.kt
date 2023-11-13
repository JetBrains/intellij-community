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
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.util.SeqScope
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

internal class ScriptingModuleInfoProviderExtension : ModuleInfoProviderExtension {
    override fun SeqScope<Result<IdeaModuleInfo>>.collectByElement(
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

    override fun SeqScope<Result<IdeaModuleInfo>>.collectByFile(
        project: Project,
        virtualFile: VirtualFile,
        isLibrarySource: Boolean,
        existingInfos: Collection<IdeaModuleInfo>?
    ) {
        val isBinary = virtualFile.fileType.isKotlinBinary

        if (isBinary && virtualFile in ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFilesScope()) {
            if (isLibrarySource) {
                register(ScriptDependenciesSourceInfo.ForProject(project))
            } else {
                val existing = existingInfos?.find { it is ScriptDependenciesInfo.ForFile }
                register(existing ?: ScriptDependenciesInfo.ForProject(project))
            }
        }

        register {
            if (!isBinary && virtualFile in ScriptConfigurationManager.getInstance(project).getAllScriptDependenciesSourcesScope()) {
                ScriptDependenciesSourceInfo.ForProject(project)
            } else {
                null
            }
        }
    }

    override fun SeqScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile) {
        yield {
            if (ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType) {
                ScriptRelatedModuleNameFile[project, virtualFile]?.let { scratchModuleName ->
                    val moduleManager = ModuleManager.getInstance(project)
                    moduleManager.findModuleByName(scratchModuleName)
                }
            } else {
                null
            }
        }
    }
}