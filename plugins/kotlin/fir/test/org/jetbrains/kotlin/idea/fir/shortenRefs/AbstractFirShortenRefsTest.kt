// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.shortenRefs

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File

abstract class AbstractFirShortenRefsTest : AbstractImportsTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun doTest(file: KtFile): String? {
        val selectionModel = myFixture.editor.selectionModel
        if (!selectionModel.hasSelection()) error("No selection in input file")

        val selection = runReadAction { TextRange(selectionModel.selectionStart, selectionModel.selectionEnd) }

        val shortenings = executeOnPooledThreadInReadAction {
            analyze(file) {
                if (file.text.contains("// SHORTEN_AND_STAR_IMPORT")) {
                    collectPossibleReferenceShortenings(file,
                                                        selection,
                                                        shortenOptions = ShortenOptions.ALL_ENABLED,
                                                        classShortenStrategy = { ShortenStrategy.SHORTEN_AND_STAR_IMPORT },
                                                        callableShortenStrategy = { ShortenStrategy.SHORTEN_AND_STAR_IMPORT })
                } else if (file.text.contains("// SHORTEN_AND_IMPORT")) {
                    collectPossibleReferenceShortenings(file,
                                                        selection,
                                                        shortenOptions = ShortenOptions.ALL_ENABLED,
                                                        classShortenStrategy = { ShortenStrategy.SHORTEN_AND_IMPORT },
                                                        callableShortenStrategy = { ShortenStrategy.SHORTEN_AND_IMPORT })
                } else {
                    collectPossibleReferenceShortenings(file, selection, shortenOptions = ShortenOptions.ALL_ENABLED)
                }
            }
        }

        project.executeWriteCommand("") {
            val shortenedElements = shortenings.invokeShortening()
            val shorteningResultAsString = shortenedElements.joinToString(System.lineSeparator()) { it.text }

            KotlinTestUtils.assertEqualsToFile(getShorteningResultFile(), shorteningResultAsString)
        }

        selectionModel.removeSelection()
        return null
    }

    override val runTestInWriteCommand: Boolean = false

    protected fun doTestWithMuting(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2, ".after") {
            doTest(unused)
        }
    }

    override val nameCountToUseStarImportDefault: Int
        get() = Integer.MAX_VALUE

    private fun getShorteningResultFile(): File = dataFile().withExtension("txt")
}
