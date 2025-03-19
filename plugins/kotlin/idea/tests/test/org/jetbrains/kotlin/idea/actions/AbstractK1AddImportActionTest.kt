// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import org.jetbrains.kotlin.idea.base.test.IgnoreTests

abstract class AbstractK1AddImportActionTest : AbstractAddImportActionTestBase() {
    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.IGNORE_K1) {
            super.doTest(unused)
        }
    }
}