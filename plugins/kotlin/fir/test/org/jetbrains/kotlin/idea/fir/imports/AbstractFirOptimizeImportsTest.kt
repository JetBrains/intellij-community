// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.imports

import com.intellij.lang.ImportOptimizer
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.imports.KotlinFirImportOptimizer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirJvmOptimizeImportsTest : AbstractImportsTest() {
    override val runTestInWriteCommand: Boolean = false

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(
            testDataFile().toPath(),
            IgnoreTests.DIRECTIVES.FIR_COMPARISON,
            ".after",
            test = { super.doTest(unused) }
        )
    }

    override fun doTest(file: KtFile): String? {
        val optimizer = executeOnPooledThreadInReadAction {
            KotlinFirImportOptimizer().processFile(file)
        }!!

        project.executeWriteCommand("") {
            optimizer.run()
        }

        return null
    }
}