// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.modules

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProviderExtension
import org.jetbrains.kotlin.idea.base.projectStructure.isKotlinBinary
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.register
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.v1.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.yieldIfNotNull

@OptIn(K1ModeProjectStructureApi::class)
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
      isLibrarySource: Boolean,
      config: ModuleInfoProvider.Configuration,
    ) {
        val isBinary = virtualFile.fileType.isKotlinBinary

        if (isBinary) {
            if (ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope().contains(virtualFile)) {
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

    override suspend fun SequenceScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile) {
        if (ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType) {
            ScriptRelatedModuleNameFile.Companion[project, virtualFile]?.let { scratchModuleName ->
                val moduleManager = ModuleManager.getInstance(project)
                yieldIfNotNull(moduleManager.findModuleByName(scratchModuleName))
            }
        }
    }
}