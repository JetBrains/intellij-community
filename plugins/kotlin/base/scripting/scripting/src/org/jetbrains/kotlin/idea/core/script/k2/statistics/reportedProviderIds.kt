// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.statistics

import org.jetbrains.kotlin.idea.core.script.k2.definitions.KotlinScriptDefinitionsProviderId
import org.jetbrains.kotlin.idea.core.script.k2.definitions.SCRIPT_DEFINITIONS_PROVIDER_ID
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

internal const val OTHER_PROVIDER_ID: String = "other"
internal const val BUNDLED_DEFAULT_ID: String = "BundledDefault"

internal val BUNDLED_PROVIDER_IDS: List<String> = KotlinScriptDefinitionsProviderId.entries.map { it.id }

internal val REPORTED_PROVIDER_IDS: List<String> = buildList {
    addAll(BUNDLED_PROVIDER_IDS)
    add(BUNDLED_DEFAULT_ID)
    add(OTHER_PROVIDER_ID)
}

internal fun ScriptDefinition.reportedProviderId(): String = when {
    isDefault -> BUNDLED_DEFAULT_ID
    else -> getUserData(SCRIPT_DEFINITIONS_PROVIDER_ID)?.takeIf { it in BUNDLED_PROVIDER_IDS } ?: OTHER_PROVIDER_ID
}
