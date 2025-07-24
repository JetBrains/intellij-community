// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

/**
 * For using in DefaultScriptConfigurationManager and in tests only
 */
fun areSimilar(old: ScriptCompilationConfigurationWrapper, new: ScriptCompilationConfigurationWrapper): Boolean {
    if (old.script != new.script) return false

    val oldConfig = old.configuration
    val newConfig = new.configuration

    if (oldConfig == newConfig) return true
    if (oldConfig == null || newConfig == null) return false

    if (oldConfig[ScriptCompilationConfiguration.jvm.jdkHome] != newConfig[ScriptCompilationConfiguration.jvm.jdkHome]) return false

    // there is differences how script definition classpath is added to script classpath in old and new scripting API,
    // so it's important to compare the resulting classpath list, not only the value of key
    if (oldConfig[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty() != newConfig[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty()) return false

    if (oldConfig[ScriptCompilationConfiguration.ide.dependenciesSources] != newConfig[ScriptCompilationConfiguration.ide.dependenciesSources]) return false
    if (oldConfig[ScriptCompilationConfiguration.defaultImports] != newConfig[ScriptCompilationConfiguration.defaultImports]) return false

    return true
}
