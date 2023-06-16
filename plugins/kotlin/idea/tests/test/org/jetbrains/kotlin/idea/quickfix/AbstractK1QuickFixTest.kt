// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths

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

    override fun doTest(beforeFileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(beforeFileName), IgnoreTests.DIRECTIVES.IGNORE_FE10) {
            super.doTest(beforeFileName)
        }

    }
}