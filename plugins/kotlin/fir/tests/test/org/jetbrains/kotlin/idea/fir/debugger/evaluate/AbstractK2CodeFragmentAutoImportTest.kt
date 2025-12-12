// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.debugger.evaluate

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractCodeFragmentAutoImportTest
import java.nio.file.Paths

abstract class AbstractK2CodeFragmentAutoImportTest : AbstractCodeFragmentAutoImportTest() {
    override fun configureByCodeFragment(filePath: String) {
        myFixture.configureByK2ModeCodeFragment(filePath)
    }

    override fun doTest(filePath: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(filePath),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doTest(filePath)
        }
    }
}