// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProviderExtension
import org.jetbrains.kotlin.idea.base.projectStructure.isKotlinBinary
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.register
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.scriptLibraryDependencies
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.SeqScope
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

@OptIn(K1ModeProjectStructureApi::class)
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
        config: ModuleInfoProvider.Configuration,
    ) {
        val isBinary = virtualFile.fileType.isKotlinBinary

        if (isBinary) {
            if (KotlinPluginModeProvider.isK2Mode()) {
                val scriptFile = (config.contextualModuleInfo as? ScriptModuleInfo)?.scriptFile
                scriptFile?.scriptLibraryDependencies(project)?.filter { virtualFile in it.contentScope }?.forEach(::register)
            } else if (ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope().contains(virtualFile)) {
                if (isLibrarySource) {
                    register(ScriptDependenciesSourceInfo.ForProject(project))
                } else {
                    val scriptFile = when (val scriptModuleInfo = config.contextualModuleInfo) {
                        is ScriptModuleInfo -> scriptModuleInfo.scriptFile
                        is ScriptDependenciesInfo.ForFile -> scriptModuleInfo.scriptFile
                        else -> null
                    }

                    if (scriptFile != null) {
                        register {
                            ScriptDependenciesInfo.ForFile(
                                project,
                                scriptFile,
                                findScriptDefinition(project, VirtualFileScriptSource(scriptFile))
                            )
                        }
                    } else {
                        register(ScriptDependenciesInfo.ForProject(project))
                    }
                }
            }
        } else {
            register {
                if (ScriptDependencyAware.getInstance(project).getAllScriptDependenciesSourcesScope().contains(virtualFile)) {
                    ScriptDependenciesSourceInfo.ForProject(project)
                } else {
                    null
                }
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