// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import kotlin.script.dependencies.Environment
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration


class CustomScriptTemplateProvider(val environment: Environment) : ScriptDefinitionsSource {

    override val definitions: Sequence<ScriptDefinition>
        get() = loadDefinitionsFromTemplates(
            templateClassNames = environment["template-classes-names"] as List<String>,
            templateClasspath = environment["template-classes"]?.let { it as? List<File> } ?: emptyList(),
            baseHostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                getEnvironment { environment }
            }
        ).asSequence()

}

class FromTextTemplateProvider(val environment: Environment) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = loadDefinitionsFromTemplates(
            templateClassNames = listOf("org.jetbrains.kotlin.idea.script.Template"),
            templateClasspath = environment["template-classes"]?.let { it as? List<File> } ?: emptyList(),
            baseHostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                getEnvironment { environment }
            }
        ).asSequence()
}