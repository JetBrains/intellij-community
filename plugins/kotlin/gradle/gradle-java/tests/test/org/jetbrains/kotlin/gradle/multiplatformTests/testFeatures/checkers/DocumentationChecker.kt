// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.kdoc.findKDoc
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.nio.file.Path
import kotlin.io.path.relativeTo
import kotlin.test.assertEquals

class DocumentationCheckerConfig {
    var downloadSources: Boolean = false
}

interface DocumentationCheckerDsl {
    private val TestConfigurationDslScope.configuration
        get() = writeAccess.getConfiguration(DocumentationChecker)

    var TestConfigurationDslScope.downloadSources: Boolean
        get() = configuration.downloadSources
        set(value) {
            configuration.downloadSources = value
        }
}

object DocumentationChecker : TestFeature<DocumentationCheckerConfig> {
    private const val EXPECTED_TEST_DATA = "expected-doc.txt"
    private const val CARET_PLACEHOLDER = "<caret:doc>"
    private val docCaret = Regex(CARET_PLACEHOLDER)

    private data class CaretPosition(val file: Path, val line: Int, val offset: Int)
    private val carets = mutableListOf<CaretPosition>()

    override fun createDefaultConfiguration() = DocumentationCheckerConfig()

    override fun KotlinMppTestsContext.beforeTestExecution() {
        val config = testConfiguration.getConfiguration(DocumentationChecker)
        if (config.downloadSources) {
            // in old branches sources downloaded by default
            //GradleSettings.getInstance(testProject).isDownloadSources = true
        }
        carets.clear()
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
                val docText = navigationElement.findKDoc()?.contentTag?.getContent()
                return@map """
                    |$relativePath:$line:$offset
                    |$docText
                """.trimMargin()
            }.joinToString("\n\n")
        }
        assertEqualsToFile("Documentation content", testDataDirectory.resolve(EXPECTED_TEST_DATA), actualResult)
    }
}