// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.util.PropertiesCollection

/**
 * Where a script definition comes from.
 */
internal sealed interface ScriptDefinitionDiscoverySource {
    /**
     * A definition that a plugin supplied, through the `ScriptDefinitionsProvider` extension point or
     * as the bundled default.
     */
    data class Provider(
        val implementationFqn: String,
        val pluginName: String? = null,
        val pluginId: String? = null,
    ) : ScriptDefinitionDiscoverySource

    /**
     * A definition discovered through a marker file under `META-INF/kotlin/script/templates`.
     */
    data class MarkerFile(
        val rootName: String? = null,
        val jarPath: String? = null,
        val displayLocation: String? = null,
    ) : ScriptDefinitionDiscoverySource

    /**
     * A definition class that the user listed in the settings, with no marker file for it.
     */
    data object Manual : ScriptDefinitionDiscoverySource
}

/**
 * Where the IDE found the definition.
 *
 * This is a configuration key and not user data, so it survives every rebuild of the definition. A
 * definition object built outside the cache still answers, which user data on one instance cannot do.
 */
internal val IdeScriptCompilationConfigurationKeys.discoverySource: PropertiesCollection.Key<ScriptDefinitionDiscoverySource>
        by PropertiesCollection.key()
