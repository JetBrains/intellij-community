// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.highlighting.AbstractScriptHighlightHelper
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

class ScriptHighlightHelper(val project: Project) : AbstractScriptHighlightHelper() {
    override fun isReadyToHighlight(file: KtFile): Boolean {
        return ScriptConfigurationsProvider.getInstance(project)?.getScriptConfigurationResult(file) != null
    }
}