// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import org.jetbrains.kotlin.idea.base.test.IgnoreTests

abstract class AbstractK1JvmBasicCompletionTest : AbstractJvmBasicCompletionTestBase() {
    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1) {
            super.executeTest(test)
        }
    }
}