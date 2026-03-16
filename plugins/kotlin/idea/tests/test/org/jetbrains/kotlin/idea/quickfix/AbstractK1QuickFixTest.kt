// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1QuickFixTest : AbstractQuickFixTest() {
    override fun setUp() {
        super.setUp()
        addJavaLangRecordClass()
    }

    // Needed to make the Kotlin compiler think it is running on JDK 16+
    // see org.jetbrains.kotlin.resolve.jvm.checkers.JvmRecordApplicabilityChecker
    private fun addJavaLangRecordClass() {
        myFixture.addClass(
            """
            package java.lang;
            public abstract class Record {}
            """.trimIndent()
        )
    }

    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)
    }

    override fun loadScriptConfiguration(file: KtFile) {
        ScriptConfigurationManager.getInstanceSafe(project)?.getConfiguration(file)
    }

    override val actionPrefix: String? = "K1_ACTION:"
}