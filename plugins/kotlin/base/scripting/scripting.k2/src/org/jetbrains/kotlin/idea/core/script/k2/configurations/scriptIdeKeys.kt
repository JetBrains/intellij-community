// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.util.PropertiesCollection

val IdeScriptCompilationConfigurationKeys.scriptEntityProvider: PropertiesCollection.Key<() -> KotlinScriptEntityProvider> by PropertiesCollection.key()

fun ScriptDefinition.getScriptEntityProvider(project: Project): KotlinScriptEntityProvider =
    compilationConfiguration[ScriptCompilationConfiguration.ide.scriptEntityProvider]?.invoke()
        ?: DefaultKotlinScriptEntityProvider.getInstance(project)
