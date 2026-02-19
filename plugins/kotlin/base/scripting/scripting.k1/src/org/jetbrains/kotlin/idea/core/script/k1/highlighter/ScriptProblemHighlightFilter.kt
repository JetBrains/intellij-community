// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.highlighter

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptCachedProblemHighlightFilter
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

@K1Deprecation
class ScriptProblemHighlightFilter : KotlinScriptCachedProblemHighlightFilter() {
    override fun shouldHighlightScript(file: KtFile): Boolean {
        return ScriptConfigurationsProvider.getInstance(file.project)?.getScriptConfigurationResult(file) != null
    }
}