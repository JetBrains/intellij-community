// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import org.jetbrains.kotlin.idea.core.script.shared.AbstractKotlinScriptHighlightingExtension
import org.jetbrains.kotlin.psi.KtFile

class ScriptHighlightingExtension : AbstractKotlinScriptHighlightingExtension {
    override fun calculateShouldHighlightScript(file: KtFile): Boolean {
        val strategy = DefaultScriptResolutionStrategy.getInstance(file.project)
        return strategy.isReadyToHighlight(file).also { isReady -> if (!isReady) strategy.execute(file) }
    }
}