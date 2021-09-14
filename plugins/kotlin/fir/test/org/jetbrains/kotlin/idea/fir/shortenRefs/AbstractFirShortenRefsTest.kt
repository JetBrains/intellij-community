// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.shortenRefs

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirShortenRefsTest : AbstractImportsTest() {
    override val captureExceptions: Boolean = false

    override fun doTest(file: KtFile): String? {
        val selectionModel = myFixture.editor.selectionModel
        if (!selectionModel.hasSelection()) error("No selection in input file")

        val selection = runReadAction { TextRange(selectionModel.selectionStart, selectionModel.selectionEnd) }

        val shortenings = executeOnPooledThreadInReadAction {
            analyse(file) {
                collectPossibleReferenceShortenings(file, selection)
            }
        }

        project.executeWriteCommand("") {
            shortenings.invokeShortening()
        }

        selectionModel.removeSelection()
        return null
    }

    override val runTestInWriteCommand: Boolean = false

    protected fun doTestWithMuting(unused: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            doTest(unused)
        }
    }

    override val nameCountToUseStarImportDefault: Int
        get() = Integer.MAX_VALUE
}
