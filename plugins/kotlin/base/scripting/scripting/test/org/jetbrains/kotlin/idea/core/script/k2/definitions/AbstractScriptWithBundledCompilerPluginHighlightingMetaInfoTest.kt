// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.jetbrains.kotlin.idea.k2.highlighting.ProjectDescriptorWithStdlibSourcesAndExtraLibraries
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration

abstract class AbstractScriptWithBundledCompilerPluginHighlightingMetaInfoTest : AbstractScriptHighlightingMetaInfoTest() {
    override fun setUp() {
        super.setUp()

        ModuleRootModificationUtil.updateModel(module) { model ->
            ProjectDescriptorWithStdlibSourcesAndExtraLibraries.configureModule(module, model)
        }
    }

    override val customDefinitionsProvider: CustomDefinitionProviderForTest
        get() = object : CustomDefinitionProviderForTest("AbstractScriptWithBundledCompilerPluginHighlightingMetaInfoTest") {
            override fun provideDefinitions(
                baseHostConfiguration: ScriptingHostConfiguration,
                loadedScriptDefinitions: List<ScriptDefinition>
            ): Iterable<ScriptDefinition> {
                val updatedConfiguration = project.defaultDefinition.compilationConfiguration.with {
                    compilerOptions.put(
                        listOf($$"-Xplugin=$KOTLIN_BUNDLED$/lib/kotlin-dataframe-compiler-plugin-experimental.jar")
                    )
                }

                return listOf(ScriptDefinition(updatedConfiguration, ScriptEvaluationConfiguration()))
            }
        }
}