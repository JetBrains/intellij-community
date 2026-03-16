// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import org.jetbrains.kotlin.findUsages.AbstractFindUsagesMultiModuleTest
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFindUsagesMultiModuleFirTest : AbstractFindUsagesMultiModuleTest() {
    override fun getTestType(): AbstractFindUsagesTest.Companion.FindUsageTestType = AbstractFindUsagesTest.Companion.FindUsageTestType.FIR

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override val ignoreLog: Boolean = true

    override fun doTest(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            getTestdataFile().toPath().resolve("directives.txt"),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doTestInternal(path)
        }
    }
}