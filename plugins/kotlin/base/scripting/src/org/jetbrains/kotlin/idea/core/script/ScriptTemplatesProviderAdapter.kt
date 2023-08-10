// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

internal class ScriptTemplatesProviderAdapter(private val templatesProvider: ScriptTemplatesProvider) : ScriptDefinitionSourceAsContributor {
    override val id: String
        get() = templatesProvider.id

    override val definitions: Sequence<ScriptDefinition>
        get() {
            return loadDefinitionsFromTemplates(
                templatesProvider.templateClassNames.toList(), templatesProvider.templateClasspath,
                ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                    getEnvironment {
                        templatesProvider.environment
                    }
                },
                templatesProvider.additionalResolverClasspath
            ).asSequence()
        }
}