// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.shared.definition.defaultDefinition
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration

abstract class AbstractK2ScriptHighlightingMetaInfoTest : AbstractK2HighlightingMetaInfoTest() {
    /**
     * Is used to override script compilation configuration for test scripts using test directives.
     * Initial [ScriptCompilationConfiguration] is the same as default for ScriptingSupport in K2.
     */
    protected open fun refineScriptCompilationConfiguration(
        globalDirectives: Directives,
        configuration: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        return configuration
    }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val scriptFile = files.filterIsInstance<KtFile>().firstOrNull { it.isScript() }
        if (scriptFile != null) {
            registerScriptTestDefinition(project, globalDirectives)
            runWithModalProgressBlocking(project, "AbstractK2LocalInspectionTest") {
                KotlinScriptResolutionService.getInstance(project).process(scriptFile.alwaysVirtualFile)
            }
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

    /**
     * If a custom script definition is required, see [org.jetbrains.kotlin.idea.k2.highlighting.TestCustomScriptDefinition].
     */
    private class RefinedDefinitionSource(private val targetDefinition: ScriptDefinition) : ScriptDefinitionsSource {
        override val definitions: Sequence<ScriptDefinition>
            get() = sequenceOf(targetDefinition)
    }
}