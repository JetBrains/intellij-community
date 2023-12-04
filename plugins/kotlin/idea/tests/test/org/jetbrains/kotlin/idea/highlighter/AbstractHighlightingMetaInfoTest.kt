// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration.DescriptionRenderingOption
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration.SeverityRenderingOption
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.*

abstract class AbstractHighlightingMetaInfoTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    protected val HIGHLIGHTING_EXTENSION = "highlighting"

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val expectedHighlighting = dataFile().getExpectedHighlightingFile()
        val psiFile = files.first()
        if (psiFile is KtFile && psiFile.isScript()) {
            ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
        }

        checkHighlighting(psiFile, expectedHighlighting, globalDirectives)
    }

    private fun checkHighlighting(file: PsiFile, expectedHighlightingFile: File, globalDirectives: Directives) {
        val highlightingRenderConfiguration = HighlightingConfiguration(
            descriptionRenderingOption = DescriptionRenderingOption.IF_NOT_NULL,
            renderSeverityOption = SeverityRenderingOption.ONLY_NON_INFO,
            renderHighlightingAttributesKey = HIGHLIGHTER_ATTRIBUTES_KEY in globalDirectives,
            severityLevel = if (CHECK_SYMBOL_NAMES in globalDirectives) HighlightInfoType.SYMBOL_TYPE_SEVERITY else HighlightSeverity.INFORMATION
        )

        val codeMetaInfoTestCase = CodeMetaInfoTestCase(
            codeMetaInfoTypes = listOf(highlightingRenderConfiguration),
            filterMetaInfo = createMetaInfoFilter(
                allowErrorHighlighting = ALLOW_ERRORS in globalDirectives,
                highlightWarnings = HIGHLIGHT_WARNINGS in globalDirectives,
            ),
            dumbMode = DUMB_MODE in globalDirectives
        )

        codeMetaInfoTestCase.checkFile(file.virtualFile, expectedHighlightingFile, project)
    }

    /**
     * This filter serves two purposes:
     * - Fail the test on ERRORS, since we don't want to have ERRORS in the tests for a regular highlighting
     * - Filter WARNINGS, since they're provided by the compiler and (usually) aren't interesting to us in the context of highlighting
     * - Filter exact highlightings duplicates. It is a workaround about a bug in old FE10 highlighting, which sometimes highlights
     * something twice
     */
    private fun createMetaInfoFilter(allowErrorHighlighting: Boolean, highlightWarnings: Boolean): (CodeMetaInfo) -> Boolean {
        val forbiddenSeverities = setOf(HighlightSeverity.ERROR)

        val ignoredSeverities = setOf(HighlightSeverity.WARNING, HighlightSeverity.WEAK_WARNING)

        val seenMetaInfos = ObjectOpenCustomHashSet(object : Hash.Strategy<HighlightingCodeMetaInfo> {
            override fun equals(left: HighlightingCodeMetaInfo?, right: HighlightingCodeMetaInfo?): Boolean {
                if (left === right) {
                    return true
                }
                if (left == null || right == null) {
                    return false
                }

                return left.start == right.start &&
                        left.end == right.end &&
                        left.tag == right.tag &&
                        left.highlightingInfo == right.highlightingInfo
            }

            override fun hashCode(metaInfo: HighlightingCodeMetaInfo?): Int {
                if (metaInfo == null) {
                    return 0
                }
                return Objects.hash(metaInfo.start, metaInfo.end, metaInfo.tag)
            }
        })

        return filter@{ metaInfo ->
            require(metaInfo is HighlightingCodeMetaInfo)
            val highlightingInfo = metaInfo.highlightingInfo

            require(highlightingInfo.severity !in forbiddenSeverities || allowErrorHighlighting) {
                """
                    |Severity ${highlightingInfo.severity} should never appear in highlighting tests. Please, correct the testData.
                    |HighlightingInfo=$highlightingInfo
                """.trimMargin()
            }

            if (highlightingInfo.severity in ignoredSeverities && !highlightWarnings) return@filter false

            seenMetaInfos.add(metaInfo)
        }
    }

    protected fun File.getExpectedHighlightingFile(suffix: String = highlightingFileNameSuffix(this)): File {
        return resolveSibling("$name.$suffix")
    }

    protected open fun highlightingFileNameSuffix(ktFilePath: File): String = HIGHLIGHTING_EXTENSION

    companion object {
        private const val ALLOW_ERRORS = "ALLOW_ERRORS"
        private const val HIGHLIGHT_WARNINGS = "HIGHLIGHT_WARNINGS"
        private const val HIGHLIGHTER_ATTRIBUTES_KEY = "HIGHLIGHTER_ATTRIBUTES_KEY"
        private const val CHECK_SYMBOL_NAMES = "CHECK_SYMBOL_NAMES"
        private const val DUMB_MODE = "DUMB_MODE"
    }
}