// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment.k1

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArgumentsConfigurator
import org.jetbrains.kotlin.cli.common.arguments.Freezable
import org.jetbrains.kotlin.cli.common.arguments.copyCommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class AssignmentAnnotationNamesCache(project: Project) {
    companion object {
        const val ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:$ANNOTATION_OPTION_NAME="
    }

    private val moduleCache = CachedAnnotationNames(project, ANNOTATION_OPTION_PREFIX)

    private val scriptDefinitionCache: CachedValue<ConcurrentMap<String, List<String>>> = cachedValue(project) {
        CachedValueProvider.Result.create(
            ContainerUtil.createConcurrentWeakMap<String, List<String>>(),
            ProjectRootModificationTracker.getInstance(project)
        )
    }

    private val psiFileCache: CachedValue<ConcurrentMap<String, List<String>>> = cachedValue(project) {
        CachedValueProvider.Result.create(
            ContainerUtil.createConcurrentWeakMap<String, List<String>>(),
            ProjectRootModificationTracker.getInstance(project)
        )
    }

    fun getNamesForScriptDefinition(scriptDefinition: ScriptDefinition): List<String> {
        return scriptDefinitionCache.value.getOrPut(scriptDefinition.definitionId) {
            scriptDefinition.getSpecialAnnotations(ANNOTATION_OPTION_PREFIX)
        }
    }

    fun getNamesForPsiFile(psiFile: PsiFile): List<String> {
        return psiFileCache.value.getOrPut(psiFile.getPathSafely()) {
            val scriptDefinition = psiFile.findScriptDefinition() ?: return emptyList()
            scriptDefinition.getSpecialAnnotations(ANNOTATION_OPTION_PREFIX)
        }
    }

    fun getNamesForModule(module: Module): List<String> {
        return moduleCache.getNamesForModule(module)
    }

    /**
     * Sometimes virtual file can be null, e.g. on copy/paste where dummy-original.kts is passed,
     * so we have to have some additional checks.
     */
    private fun PsiFile.getPathSafely(): String {
        return when {
            virtualFile != null && this is KtFile -> virtualFilePath
            virtualFile != null -> virtualFile.path
            originalFile.virtualFile != null -> originalFile.virtualFile.path
            else -> viewProvider.virtualFile.path
        }
    }

    private fun ScriptDefinition.getSpecialAnnotations(annotationPrefix: String): List<String> {
        class CommonCompilerArgumentsHolder: CommonCompilerArguments() {
            override fun copyOf(): Freezable = copyCommonCompilerArguments(this, CommonCompilerArgumentsHolder())

            @get:Transient
            @field:kotlin.jvm.Transient
            override val configurator: CommonCompilerArgumentsConfigurator = CommonCompilerArgumentsConfigurator()
        }

        val arguments = CommonCompilerArgumentsHolder()
        parseCommandLineArguments(compilerOptions.toList(), arguments)
        return arguments.pluginOptions
            ?.filter { it.startsWith(annotationPrefix) }
            ?.map { it.substring(annotationPrefix.length) }
            ?: emptyList()
    }

    private fun <T> cachedValue(project: Project, result: () -> CachedValueProvider.Result<T>): CachedValue<T> {
        return CachedValuesManager.getManager(project).createCachedValue(result, false)
    }
}
