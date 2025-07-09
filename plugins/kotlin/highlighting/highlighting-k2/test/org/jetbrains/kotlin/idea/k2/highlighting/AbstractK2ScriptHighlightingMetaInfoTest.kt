// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.defaultDefinition
import org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getConfigurationResolver
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isScript
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.script.experimental.api.ScriptCompilationConfiguration

abstract class AbstractK2ScriptHighlightingMetaInfoTest : AbstractK2HighlightingMetaInfoTest() {
    /**
     * Is used to override script compilation configuration for test scripts using test directives.
     * Initial [ScriptCompilationConfiguration] is the same as default for ScriptingSupport in K2.
     */
    protected open fun refineScriptCompilationConfiguration(
        globalDirectives: Directives,
        configuration: ScriptCompilationConfiguration
    ) : ScriptCompilationConfiguration {
        return configuration
    }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val scriptFile = files.firstOrNull { it.isScript() }
        if (scriptFile != null) {
            registerScriptTestDefinition(project, globalDirectives)
        }

        if (scriptFile is KtFile) {
            processKotlinScriptIfNeeded(scriptFile)
        }

        super.doMultiFileTest(files, globalDirectives)
    }

    private fun registerScriptTestDefinition(project: Project, globalDirectives: Directives) {
        val refinedDef = TestCustomScriptDefinition(
            refineScriptCompilationConfiguration(
                globalDirectives,
                project.defaultDefinition.compilationConfiguration
            ), evaluationConfiguration = null
        )
        project.registerExtension(SCRIPT_DEFINITIONS_SOURCES, RefinedDefinitionSource(refinedDef), testRootDisposable)
    }

    private fun processKotlinScriptIfNeeded(ktFile: KtFile) {
        project.replaceService(
            DefaultScriptConfigurationHandler::class.java,
            DefaultScriptConfigurationHandlerForTests(project), testRootDisposable
        )

        runWithModalProgressBlocking(project, "AbstractK2LocalInspectionTest") {
            ktFile.findScriptDefinition()?.let {
                it.getConfigurationResolver(project).create(ktFile.alwaysVirtualFile, it)
            }
        }
    }

    /**
     * If a custom script definition is required, see [org.jetbrains.kotlin.idea.k2.highlighting.TestCustomScriptDefinition].
     */
    private class RefinedDefinitionSource(private val targetDefinition: ScriptDefinition) : ScriptDefinitionsSource {
        override val definitions: Sequence<ScriptDefinition>
            get() = sequenceOf(targetDefinition)
    }
    // kotlin scripts require adjusting a project model which is not possible for lightweight test fixture
    private class DefaultScriptConfigurationHandlerForTests(testProject: Project) :
        DefaultScriptConfigurationHandler(testProject, CoroutineScope(EmptyCoroutineContext)) {
        override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {}

        override fun isScriptExist(
            project: Project,
            scriptFile: VirtualFile,
            definition: ScriptDefinition
        ): Boolean = true
    }
}