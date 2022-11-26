// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.highlighter.markers.TestableLineMarkerNavigator
import org.jetbrains.kotlin.idea.navigation.NavigationTestUtils
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.junit.Assert
import java.io.File

abstract class AbstractLineMarkersTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(path: String) = doTest(path) {}

    protected fun doAndCheckHighlighting(
        psiFile: PsiFile,
        documentToAnalyze: Document,
        expectedHighlighting: KotlinExpectedHighlightingData,
        expectedFile: File
    ): List<LineMarkerInfo<*>> {
        myFixture.doHighlighting()

        return checkHighlighting(psiFile, documentToAnalyze, expectedHighlighting, expectedFile)
    }

    fun doTest(unused: String, additionalCheck: () -> Unit) {
        val fileText = FileUtil.loadFile(dataFile())
        try {
            ConfigLibraryUtil.configureLibrariesByDirective(myFixture.module, fileText)
            if (InTextDirectivesUtils.findStringWithPrefixes(fileText, "METHOD_SEPARATORS") != null) {
                DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = true
            }

            val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")
            for (suffix in dependencySuffixes) {
                val dependencyPath = fileName().replace(".kt", suffix)
                if (File(testDataDirectory, dependencyPath).exists()) {
                    myFixture.configureByFile(dependencyPath)
                }
            }

            myFixture.configureByFile(fileName())
            val project = myFixture.project
            val document = myFixture.editor.document

            val ktFile = myFixture.file as KtFile

            val data = KotlinExpectedHighlightingData(document)
            data.init()

            PsiDocumentManager.getInstance(project).commitAllDocuments()

            val markers = doAndCheckHighlighting(ktFile, document, data, dataFile())

            assertNavigationElements(myFixture.project, ktFile, markers)
            additionalCheck()
        } catch (exc: Exception) {
            throw RuntimeException(exc)
        } finally {
            ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = false
        }

    }

    companion object {

        @Suppress("SpellCheckingInspection")
        private const val LINE_MARKER_PREFIX = "LINEMARKER:"
        private const val TARGETS_PREFIX = "TARGETS"

        fun assertNavigationElements(project: Project, file: KtFile, markers: List<LineMarkerInfo<*>>) {
            val navigationDataComments = KotlinTestUtils.getLastCommentsInFile(
                file, KotlinTestUtils.CommentType.BLOCK_COMMENT, false
            )
            if (navigationDataComments.isEmpty()) return
            val markerCodeMetaInfos = markers.map { InnerLineMarkerCodeMetaInfo(InnerLineMarkerConfiguration.configuration, it) }

            for ((navigationCommentIndex, navigationComment) in navigationDataComments.reversed().withIndex()) {
                val description = getLineMarkerDescription(navigationComment)
                val navigateMarkers = markerCodeMetaInfos.filter { it.asString() == description }
                val navigateMarker = navigateMarkers.singleOrNull() ?: navigateMarkers.getOrNull(navigationCommentIndex)

                TestCase.assertNotNull(
                    String.format("Can't find marker for navigation check with description \"%s\"\n\navailable: \n\n%s",
                                  description,
                                  markerCodeMetaInfos.joinToString("\n\n") { it.asString() }),
                    navigateMarker
                )

                val lineMarker = navigateMarker!!.lineMarker

                val handler = lineMarker.navigationHandler
                if (handler is TestableLineMarkerNavigator) {
                    val navigateElements = handler.getTargetsPopupDescriptor(lineMarker.element)?.targets?.sortedBy {
                        it.renderAsGotoImplementation()
                    }
                    val actualNavigationData = NavigationTestUtils.getNavigateElementsText(project, navigateElements)

                    UsefulTestCase.assertSameLines(getExpectedNavigationText(navigationComment), actualNavigationData)
                } else {
                    Assert.fail("Only TestableLineMarkerNavigator are supported in navigate check")
                }
            }
        }

        private fun getLineMarkerDescription(navigationComment: String): String {
            val firstLineEnd = navigationComment.indexOf("\n")
            TestCase.assertTrue(
                "The first line in block comment must contain description of marker for navigation check", firstLineEnd != -1
            )

            var navigationMarkerText = navigationComment.substring(0, firstLineEnd)

            TestCase.assertTrue(
                String.format("Add %s directive in first line of comment", LINE_MARKER_PREFIX),
                navigationMarkerText.startsWith(LINE_MARKER_PREFIX)
            )

            navigationMarkerText = navigationMarkerText.substring(LINE_MARKER_PREFIX.length)

            return navigationMarkerText.trim { it <= ' ' }
        }

        private fun getExpectedNavigationText(navigationComment: String): String {
            val firstLineEnd = navigationComment.indexOf("\n")

            var expectedNavigationText = navigationComment.substring(firstLineEnd + 1)

            TestCase.assertTrue(
                String.format("Marker %s is expected before navigation data", TARGETS_PREFIX),
                expectedNavigationText.startsWith(TARGETS_PREFIX)
            )

            expectedNavigationText = expectedNavigationText.substring(expectedNavigationText.indexOf("\n") + 1)

            return expectedNavigationText
        }

        fun checkHighlighting(
            psiFile: PsiFile,
            documentToAnalyze: Document,
            expectedHighlighting: ExpectedHighlightingData,
            expectedFile: File
        ): MutableList<LineMarkerInfo<*>> {
            val markers = DaemonCodeAnalyzerImpl.getLineMarkers(documentToAnalyze, psiFile.project)

            try {
                expectedHighlighting.checkLineMarkers(psiFile, markers, documentToAnalyze.text)

                // This is a workaround for sad bug in ExpectedHighlightingData:
                // the latter doesn't throw assertion error when some line markers are expected, but none are present.
                if (FileUtil.loadFile(expectedFile).contains("<lineMarker") && markers.isEmpty()) {
                    throw AssertionError("Some line markers are expected, but nothing is present at all")
                }
            } catch (failure: FileComparisonFailure) {
                throw failure
            } catch (error: AssertionError) {
                try {
                    val actualTextWithTestData = TagsTestDataUtil.insertInfoTags(markers, true, documentToAnalyze.text)
                    KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithTestData)
                } catch (failure: FileComparisonFailure) {
                    throw FileComparisonFailure(
                        error.message + "\n" + failure.message,
                        failure.expected,
                        failure.actual,
                        failure.filePath
                    )
                }
            }
            return markers
        }
    }

}
