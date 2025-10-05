// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.highlighter

import org.jetbrains.kotlin.idea.core.script.shared.AbstractKotlinScriptHighlightingExtension
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

class
ScriptHighlightingExtension : AbstractKotlinScriptHighlightingExtension {
    override fun calculateShouldHighlightScript(file: KtFile): Boolean =
        ScriptConfigurationsProvider.getInstance(file.project)?.getScriptConfigurationResult(file) != null
}