// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractK1JSBasicCompletionTest : AbstractJSBasicCompletionTestBase() {
    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_FE10) {
            super.executeTest(test)
        }
    }
}