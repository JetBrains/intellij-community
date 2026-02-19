// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmBasicCompletionTestBase
import java.nio.file.Paths

abstract class AbstractK1CodeFragmentCompletionTest : AbstractJvmBasicCompletionTestBase() {
    override fun configureFixture(testPath: String) {
        myFixture.configureByK1ModeCodeFragment(testPath)
    }

    override fun doTest(filePath: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(filePath),
            IgnoreTests.DIRECTIVES.IGNORE_K1,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doTest(filePath)
        }
    }
}
