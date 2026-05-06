// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.completion.test.AbstractKotlinSourceInJavaWithMockLibCompletionTest

abstract class AbstractK2KotlinSourceInJavaWithMockLibCompletionTest : AbstractKotlinSourceInJavaWithMockLibCompletionTest() {

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.executeTest(test)
        }
    }

}
