// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.lang.annotation.HighlightSeverity
import gnu.trove.THashSet
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingRenderConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingRenderConfiguration.DescriptionRenderingOption
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingRenderConfiguration.SeverityRenderingOption
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File
import java.util.*

abstract class AbstractHighlightingMetaInfoTest : KotlinLightCodeInsightFixtureTestCase() {
    protected val HIGHLIGHTING_EXTENSION = "highlighting"

    open fun doTest(unused: String) {
        myFixture.configureByFile(fileName())
        val expectedHighlighting = testDataFile().getExpectedHighlightingFile()

        checkHighlighting(expectedHighlighting)
    }

    private fun checkHighlighting(expectedHighlightingFile: File) {
        val highlightingRenderConfiguration = HighlightingRenderConfiguration(
            descriptionRenderingOption = DescriptionRenderingOption.IF_NOT_NULL,
            renderSeverityOption = SeverityRenderingOption.ONLY_NON_INFO,
        )

        val codeMetaInfoTestCase = CodeMetaInfoTestCase(
            codeMetaInfoTypes = listOf(highlightingRenderConfiguration),
            filterMetaInfo = createMetaInfoFilter()
        )

        codeMetaInfoTestCase.checkFile(myFixture.file.virtualFile, expectedHighlightingFile, project)
    }

    /**
     * This filter serves two purposes:
     * - Fail the test on ERRORS, since we don't want to have ERRORS in the tests for a regular highlighting
     * - Filter WARNINGS, since they are provided by the compiler and (usually) are not interesting to us in the context of highlighting
     * - Filter exact highlightings duplicates. It is a workaround about a bug in old FE10 highlighting, which sometimes highlights
     * something twice
     */
    private fun createMetaInfoFilter(): (CodeMetaInfo) -> Boolean {
        val forbiddenSeverities = setOf(HighlightSeverity.ERROR)

        val ignoredSeverities = setOf(HighlightSeverity.WARNING, HighlightSeverity.WEAK_WARNING)

        val seenMetaInfos = THashSet(object : TObjectHashingStrategy<HighlightingCodeMetaInfo> {
            override fun equals(left: HighlightingCodeMetaInfo, right: HighlightingCodeMetaInfo): Boolean =
                left.start == right.start &&
                        left.end == right.end &&
                        left.tag == right.tag &&
                        left.highlightingInfo == right.highlightingInfo

            override fun computeHashCode(metaInfo: HighlightingCodeMetaInfo): Int =
                Objects.hash(metaInfo.start, metaInfo.end, metaInfo.tag)
        })

        return { metaInfo ->
            require(metaInfo is HighlightingCodeMetaInfo)
            val highlightingInfo = metaInfo.highlightingInfo

            require(highlightingInfo.severity !in forbiddenSeverities) {
                """
                    |Severity ${highlightingInfo.severity} should never appear in highlighting tests. Please, correct the testData.
                    |HighlightingInfo=$highlightingInfo
                """.trimMargin()
            }

            highlightingInfo.severity !in ignoredSeverities && seenMetaInfos.add(metaInfo)
        }
    }

    protected fun File.getExpectedHighlightingFile(suffix: String = highlightingFileNameSuffix(this)): File {
        return resolveSibling("$name.$suffix")
    }

    protected open fun highlightingFileNameSuffix(ktFilePath: File): String = HIGHLIGHTING_EXTENSION
}