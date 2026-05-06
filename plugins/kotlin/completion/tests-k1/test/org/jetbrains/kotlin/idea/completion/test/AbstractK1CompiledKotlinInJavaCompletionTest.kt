// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import org.jetbrains.kotlin.idea.base.test.IgnoreTests

abstract class AbstractK1CompiledKotlinInJavaCompletionTest : AbstractCompiledKotlinInJavaCompletionTest() {
    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1) {
            super.executeTest(test)
        }
    }
}
