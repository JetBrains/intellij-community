// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1IntentionTest : AbstractIntentionTestBase() {
    override fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1) {
            doTestForInternal(mainFile, pathToFiles, intentionAction, fileText)
        }
    }

    protected fun doTestForInternal(
        mainFile: File,
        pathToFiles: Map<String, PsiFile>,
        intentionAction: IntentionAction,
        fileText: String
    ) {
        super.doTestFor(mainFile, pathToFiles, intentionAction, fileText)
    }

    override fun updateScriptDependenciesSynchronously() {
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
    }

    override fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)
    }

    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        if (!InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_WARNINGS_AFTER")) {
            DirectiveBasedActionUtils.checkForUnexpectedWarnings(
                ktFile,
                disabledByDefault = false,
                directiveName = "AFTER-WARNING",
                diagnosticsProvider = k1DiagnosticsProvider
            )
        }

        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)
    }
}