// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.TestFeatureWithFileMarkup
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.kdoc.findKDoc
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import java.io.File
import java.nio.file.Path
import kotlin.io.path.relativeTo

class DocumentationCheckerConfig {
    var downloadSources: Boolean = false
}

object DocumentationChecker : TestFeatureWithFileMarkup<DocumentationCheckerConfig> {
    private const val EXPECTED_TEST_DATA = "expected-doc.txt"
    private const val CARET_PLACEHOLDER = "<caret:doc>"
    private val docCaret = Regex(CARET_PLACEHOLDER)

    private data class CaretPosition(val file: Path, val line: Int, val offset: Int)
    private val carets = mutableListOf<CaretPosition>()

    override fun createDefaultConfiguration() = DocumentationCheckerConfig()

    override fun KotlinMppTestsContext.beforeTestExecution() {
        val config = testConfiguration.getConfiguration(DocumentationChecker)
        GradleSystemSettings.getInstance().isDownloadSources = config.downloadSources
        carets.clear()
    }

    override fun KotlinMppTestsContext.afterTestExecution() {
        GradleSystemSettings.getInstance().isDownloadSources = false
    }

    override fun preprocessFile(origin: File, text: String) =
        text.lines().mapIndexed { lineIndex, lineText ->
            docCaret.findAll(lineText)
                .map { it.range }
                .forEachIndexed { rangeIndex, range ->
                    val offset = range.first - rangeIndex * CARET_PLACEHOLDER.length
                    carets.add(CaretPosition(origin.toPath(), lineIndex, offset))
                }
            lineText.replace(CARET_PLACEHOLDER, "")
        }.joinToString("\n")


    override fun KotlinMppTestsContext.afterImport() {
        if (carets.isEmpty()) return
        val actualResult = runInEdtAndGet {
            val allSourceFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, ProjectScope.getProjectScope(testProject))
            carets.map { (path, line, offset) ->
                val relativePath = path.relativeTo(testDataDirectory.toPath())
                val virtualFile = allSourceFiles.singleOrNull { f ->
                    f.toNioPath().relativeTo(testProjectRoot.toPath()) == relativePath
                } ?: error("expected file '$relativePath' is not found in the imported project")
                codeInsightTestFixture.configureFromExistingVirtualFile(virtualFile)
                codeInsightTestFixture.editor.caretModel.moveToLogicalPosition(LogicalPosition(line, offset))

                val caretElement = codeInsightTestFixture.file.findElementAt(codeInsightTestFixture.caretOffset)
                    ?: error("element at caret is not found in the file '$relativePath'[$line:$offset]")
                val referenceExpression = caretElement.parent as? KtNameReferenceExpression
                    ?: error("caret is expected at reference expression")
                val element = referenceExpression.mainReference.resolve() as KtElement
                val navigationElement = element.navigationElement as? KtDeclaration
                    ?: error("documentation can be only on KtDeclaration")
                val docText: String? = navigationElement.findKDoc { DescriptorToSourceUtilsIde.getAnyDeclaration(testProject, it) }?.contentTag?.getContent()
                return@map """
                    |$relativePath:$line:$offset
                    |$docText
                """.trimMargin()
            }.joinToString("\n\n")
        }
        assertEqualsToFile("Documentation content", testDataDirectory.resolve(EXPECTED_TEST_DATA), actualResult)
    }

    override fun KotlinMppTestsContext.restoreMarkup(text: String, editor: Editor): String {
        val caretsInFile = carets.filter {
            val relativePath = it.file.relativeTo(testDataDirectory.toPath())
            editor.virtualFile.toNioPath().relativeTo(testProjectRoot.toPath()) == relativePath
        }.sortedWith { c1, c2 -> c1.line.compareTo(c2.line).takeIf { it != 0 } ?: c1.offset.compareTo(c2.offset) }

        var currentOffset = 0
        return buildString {
            for (caretPosition in caretsInFile) {
                val (_, line, column) = caretPosition
                editor.caretModel.moveToLogicalPosition(LogicalPosition(line, column))
                val offset = editor.caretModel.offset
                append(text.substring(currentOffset, offset))
                append(CARET_PLACEHOLDER)
                currentOffset = offset
            }
            append(text.substring(currentOffset))
        }
    }
}