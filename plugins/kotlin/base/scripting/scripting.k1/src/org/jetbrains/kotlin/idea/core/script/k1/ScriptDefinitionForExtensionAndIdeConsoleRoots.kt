// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.ide.scratch.RootType
import com.intellij.ide.script.IdeConsoleRootType
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.shared.definition.scriptClassPath
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.templates.standard.ScriptTemplateWithBindings

private object ScriptDefinitionForExtensionAndIdeConsoleRoots : ScriptDefinition.FromConfigurations(
    defaultJvmScriptingHostConfiguration,
    ScriptCompilationConfigurationForExtensionAndIdeConsoleRoots,
    ScriptEvaluationConfigurationForExtensionAndIdeConsoleRoots
) {
    override fun isScript(script: SourceCode): Boolean {
        val virtFileSourceCode = script as? VirtualFileScriptSource
        return virtFileSourceCode != null &&
                RootType.forFile(virtFileSourceCode.virtualFile)?.let {
                    it is ExtensionsRootType || it is IdeConsoleRootType
                } ?: false
    }
}

private const val SCRIPT_DEFINITION_NAME = "Script definition for extension scripts and IDE console"

class ScriptDefinitionForExtensionAndIdeConsoleRootsSource : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = sequenceOf(ScriptDefinitionForExtensionAndIdeConsoleRoots)
}

private object ScriptCompilationConfigurationForExtensionAndIdeConsoleRoots : ScriptCompilationConfiguration(
    {
        dependencies(JvmDependency(scriptClassPath))
        baseClass(KotlinType(ScriptTemplateWithBindings::class))
        displayName(SCRIPT_DEFINITION_NAME)
        jvm {
            val kotlincLibraryName = KotlinArtifacts.kotlinc.name
                //PathManager.getJarForClass(KotlinVersion::class.java)?.nameWithoutExtension
                //    ?: error("unable to locate Kotlin standard library")
             //This approach works, but could be quite expensive, since it forces indexing of the whole IDEA classpath
            // more economical approach would be to list names (without versions and .jar extension) of all jars
            // required for the scripts after the kotlin stdlib/script-runtime, and set wholeClasspath to false
            dependenciesFromCurrentContext(
                wholeClasspath = true
            )
            // todo commented out until we figure out why it is needed and how to implement it safely
            //PluginManagerCore.plugins.forEach {
            //    dependenciesFromClassloader(classLoader = it.classLoader, wholeClasspath = true)
            //}
        }
        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }
    }
)

private object ScriptEvaluationConfigurationForExtensionAndIdeConsoleRoots : ScriptEvaluationConfiguration({})

