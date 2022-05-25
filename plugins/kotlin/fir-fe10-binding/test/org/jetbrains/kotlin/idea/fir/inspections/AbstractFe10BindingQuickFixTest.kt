// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFe10BindingQuickFixTest : AbstractQuickFixTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doTest(beforeFileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_FE10_BINDING_BY_FIR, "after") {
            super.doTest(beforeFileName)
        }
    }

    // TODO: Enable these as more actions/inspections are enabled, and/or add more FIR-specific directives
    override fun checkForUnexpectedErrors() {}
    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {}
}