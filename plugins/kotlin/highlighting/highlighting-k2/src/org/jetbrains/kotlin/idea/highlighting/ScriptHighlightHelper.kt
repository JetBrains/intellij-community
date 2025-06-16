// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.highlighting.AbstractScriptHighlightHelper
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtFile

class ScriptHighlightHelper(val project: Project) : AbstractScriptHighlightHelper() {
    override fun isReadyToHighlight(file: KtFile): Boolean {
        val strategy = DefaultScriptResolutionStrategy.getInstance(project)
        return strategy.isReadyToHighlight(file).also { isReady -> if (!isReady) strategy.execute(file) }
    }
}