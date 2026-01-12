// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest
import org.jetbrains.kotlin.psi.KtCodeFragment
import java.io.File

abstract class AbstractK1CodeFragmentCompletionHandlerTest : AbstractCompletionHandlerTest(CompletionType.BASIC) {
    override fun setUpFixture(testPath: String) {
        myFixture.configureByK1ModeCodeFragment(dataFile(testPath).path)
    }

    override fun doTest(testPath: String) {
        super.doTest(testPath)

        // TODO move to the superclass to cover K2 once imports start working there (IDEA-354710)
        val fragment = myFixture.file as KtCodeFragment
        fragment.checkImports(File(testPath))
    }
}
