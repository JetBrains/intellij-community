// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.lang.annotation.HighlightSeverity
import gnu.trove.THashSet
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingRenderConfiguration
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
            descriptionRenderingOption = HighlightingRenderConfiguration.DescriptionRenderingOption.IF_NOT_NULL,
        )

        val codeMetaInfoTestCase = CodeMetaInfoTestCase(
            codeMetaInfoTypes = listOf(highlightingRenderConfiguration),
            filterMetaInfo = createMetaInfoFilter()
        )

        codeMetaInfoTestCase.checkFile(myFixture.file.virtualFile, expectedHighlightingFile, project)
    }

    /**
     * This filter serves two purposes:
     * - Filter WARNINGS, since they are provided by the compiler and (usually) are not interesting to us in the context of highlighting
     * - Filter exact highlightings duplicates. It is a workaround about a bug in old FE10 highlighting, which sometimes highlights
     * something twice
     */
    private fun createMetaInfoFilter(): (CodeMetaInfo) -> Boolean {
        val severitiesToFilter = setOf(
            HighlightSeverity.WARNING,
            HighlightSeverity.WEAK_WARNING
        )

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

            metaInfo.highlightingInfo.severity !in severitiesToFilter && seenMetaInfos.add(metaInfo)
        }
    }

    private fun File.getExpectedHighlightingFile(): File {
        return resolveSibling("$name.$HIGHLIGHTING_EXTENSION")
    }
}