// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1ImportsTest : AbstractImportsTest() {
    override fun updateScriptDependencies(psiFile: KtFile) {
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
    }
}