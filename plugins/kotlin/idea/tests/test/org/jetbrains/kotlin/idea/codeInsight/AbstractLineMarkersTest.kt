// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.test.InnerLineMarkerCodeMetaInfo
import org.jetbrains.kotlin.idea.base.test.InnerLineMarkerConfiguration
import org.jetbrains.kotlin.idea.base.test.KotlinExpectedHighlightingData
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.TestableLineMarkerNavigator
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
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

    protected open fun doTest(path: String) = doTest(path) {}

    protected fun doAndCheckHighlighting(
        psiFile: PsiFile,
        documentToAnalyze: Document,
        expectedHighlighting: KotlinExpectedHighlightingData,
        expectedFile: File
    ): List<LineMarkerInfo<*>> {
        myFixture.doHighlighting()

        return checkHighlighting(project, psiFile, documentToAnalyze, expectedHighlighting, expectedFile)
    }

    fun doTest(@Suppress("UNUSED_PARAMETER") unused: String, additionalCheck: () -> Unit) {
        val fileText = FileUtil.loadFile(dataFile())
        ConfigLibraryUtil.configureLibrariesByDirective(myFixture.module, testDataDirectory, fileText)
        if (InTextDirectivesUtils.findStringWithPrefixes(fileText, "METHOD_SEPARATORS") != null) {
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = true
        }
        val disabledOptions = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// OPTION: ")
            .mapNotNull {
                if (it.startsWith("-")) {
                    val optionName = it.substring(1)
                    val field = KotlinLineMarkerOptions::class.java.getDeclaredField(optionName)
                    field.isAccessible = true
                    field.get(KotlinLineMarkerOptions) as GutterIconDescriptor
                } else {
                    null
                }
            }
        disabledOptions.forEach {
            LineMarkerSettings.getSettings().setEnabled(it, false)
        }
        try {
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

            assertNavigationElements(markers)
            additionalCheck()
        } catch (exc: Exception) {
            throw RuntimeException(exc)
        } finally {
            ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = false
            disabledOptions.forEach {
                LineMarkerSettings.getSettings().setEnabled(it, true)
            }
        }
    }

    private fun assertNavigationElements(markers: List<LineMarkerInfo<*>>) {
        val navigationDataComments = KotlinTestUtils.getLastCommentsInFile(
            myFixture.file as KtFile, KotlinTestUtils.CommentType.BLOCK_COMMENT, false
        )
        if (navigationDataComments.isEmpty()) return
        val markerCodeMetaInfos = markers.map { InnerLineMarkerCodeMetaInfo(InnerLineMarkerConfiguration.configuration, it) }

        for ((navigationCommentIndex, navigationComment) in navigationDataComments.reversed().withIndex()) {
            val description = getLineMarkerDescription(navigationComment)
            val navigateMarkers = ActionUtil.underModalProgress(project, "") {
                markerCodeMetaInfos.filter { it.asString() == description }
            }
            val navigateMarker = navigateMarkers.singleOrNull() ?: navigateMarkers.getOrNull(navigationCommentIndex)

            TestCase.assertNotNull(
                String.format("Can't find marker for navigation check with description \"%s\"\n\navailable: \n\n%s",
                              description,
                              ActionUtil.underModalProgress(project, "") {
                                  markerCodeMetaInfos.joinToString("\n\n") { it.asString() }
                              }),
                navigateMarker
            )

            val lineMarker = navigateMarker!!.lineMarker

            getActualNavigationDataAndCompare(lineMarker, navigationComment)
        }
    }

    open fun getActualNavigationDataAndCompare(lineMarker: LineMarkerInfo<*>, navigationComment: String) {
        when (val handler = lineMarker.navigationHandler) {
            is InheritorsLineMarkerNavigator -> {
                val gotoData =
                    GotoImplementationHandler().createDataForSourceForTests(editor, lineMarker.element!!.parent!!)
                val targets = gotoData.targets.toMutableList().sortedBy {
                    it.renderAsGotoImplementation()
                }
                val actualNavigationData = NavigationTestUtils.getNavigateElementsText(project, targets)

                UsefulTestCase.assertSameLines(getExpectedNavigationText(navigationComment), actualNavigationData)

            }

            is TestableLineMarkerNavigator -> {
                val navigateElements = handler.getTargetsPopupDescriptor(lineMarker.element)?.targets?.sortedBy {
                    it.renderAsGotoImplementation()
                }
                val actualNavigationData = NavigationTestUtils.getNavigateElementsText(project, navigateElements)

                UsefulTestCase.assertSameLines(getExpectedNavigationText(navigationComment), actualNavigationData)
            }

            else -> {
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

    fun getExpectedNavigationText(navigationComment: String): String {
        val firstLineEnd = navigationComment.indexOf("\n")

        var expectedNavigationText = navigationComment.substring(firstLineEnd + 1)

        TestCase.assertTrue(
            String.format("Marker %s is expected before navigation data", TARGETS_PREFIX),
            expectedNavigationText.startsWith(TARGETS_PREFIX)
        )

        expectedNavigationText = expectedNavigationText.substring(expectedNavigationText.indexOf("\n") + 1)

        return expectedNavigationText
    }

    companion object {

        @Suppress("SpellCheckingInspection")
        private const val LINE_MARKER_PREFIX = "LINEMARKER:"
        private const val TARGETS_PREFIX = "TARGETS"

        fun checkHighlighting(
            project: Project,
            psiFile: PsiFile,
            documentToAnalyze: Document,
            expectedHighlighting: ExpectedHighlightingData,
            expectedFile: File
        ): List<LineMarkerInfo<*>> {
            val markers = DaemonCodeAnalyzerImpl.getLineMarkers(documentToAnalyze, psiFile.project)

            try {
                ActionUtil.underModalProgress(project, "") {
                    expectedHighlighting.checkLineMarkers(psiFile, markers, documentToAnalyze.text)
                }

                // This is a workaround for sad bug in ExpectedHighlightingData:
                // the latter doesn't throw assertion error when some line markers are expected, but none are present.
                if (FileUtil.loadFile(expectedFile).contains("<lineMarker") && markers.isEmpty()) {
                    throw AssertionError("Some line markers are expected, but nothing is present at all")
                }
            } catch (error: AssertionError) {
                if (error is FileComparisonData) {
                    throw error
                }
                try {
                    val actualTextWithTestData = TagsTestDataUtil.insertInfoTags(markers, true, documentToAnalyze.text)
                    KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithTestData)
                } catch (failure: AssertionError) {
                    if (failure !is FileComparisonData) throw failure
                    throw FileComparisonFailedError(
                        error.message + "\n" + failure.message,
                        failure.expectedStringPresentation,
                        failure.actualStringPresentation,
                        failure.filePath
                    )
                }
            }
            return markers
        }
    }

}
