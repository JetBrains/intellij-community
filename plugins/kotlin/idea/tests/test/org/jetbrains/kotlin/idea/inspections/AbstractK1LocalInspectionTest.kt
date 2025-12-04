// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1LocalInspectionTest : AbstractLocalInspectionTest() {

    override fun updateScriptDependencies(ktFile: KtFile) {
        super.updateScriptDependencies(ktFile)
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(ktFile)
    }

    override fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, diagnosticsProvider = k1DiagnosticsProvider)
    }

    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(
            ktFile, directive = DirectiveBasedActionUtils.AFTER_ERROR_DIRECTIVE, diagnosticsProvider = k1DiagnosticsProvider
        )
    }
}