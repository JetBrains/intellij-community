// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.core.script.SCRIPT_CONFIGURATIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.BundledScriptConfigurationsSource
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.DISABLE_K2_ERRORS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractK2LocalInspectionTest : AbstractLocalInspectionTest() {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val inspectionFileName: String = ".k2Inspection"

    override fun checkForUnexpectedErrors(fileText: String, beforeCheck: Boolean) {
        if (InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, IgnoreTests.DIRECTIVES.IGNORE_K2, DISABLE_ERRORS_DIRECTIVE, DISABLE_K2_ERRORS_DIRECTIVE).isNotEmpty()) {
            return
        }

        val directives =
            if (beforeCheck) {
                arrayOf("// K2-ERROR:", "// ERROR:")
            } else {
                arrayOf("// K2-AFTER-ERROR:", "// AFTER-ERROR:")
            }

        checkForUnexpected(file as KtFile, "errors", KaSeverity.ERROR, *directives)
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
    private fun checkForUnexpected(
        file: KtFile,
        name: String,
        severity: KaSeverity,
        vararg directives: String,
    ) {
        val fileText = file.text
        val (directive, lines) = directives.firstNotNullOfOrNull { directive ->
            val lines = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, directive)
            lines.takeIf { it.isNotEmpty() }?.let { directive to it }
        } ?: (directives.first() to emptyList())

        val expected = lines
            .filter { it.isNotBlank() }
            .sorted()
            .map { "$directive $it" }

        val actual =
            allowAnalysisOnEdt {
                analyze(file) {
                    val diagnostics = file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                    diagnostics
                        .filter { it.severity == severity }
                        .map { "$directive ${it.defaultMessage.replace("\n", "<br>")}" }
                        .sorted()
                }
            }

        if (actual.isEmpty() && expected.isEmpty()) return

        if (actual != expected) {
            UsefulTestCase.assertOrderedEquals(
                "All actual $name should be mentioned in test data with '$directive' directive. " +
                        "But no unnecessary $name should be mentioned, file:\n$fileText",
                actual,
                expected,
            )
        }
    }

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun getAfterTestDataAbsolutePath(mainFileName: String): Path {
        val k2Extension = IgnoreTests.FileExtension.K2
        val k2FileName = mainFileName.removeSuffix(".kt").removeSuffix(".$k2Extension") + ".$k2Extension.kt.after"
        val k2FilePath = testDataDirectory.toPath() / k2FileName
        if (k2FilePath.exists()) return k2FilePath

        return super.getAfterTestDataAbsolutePath(mainFileName)
    }

    override fun doTest(path: String) {
        val mainFile = File(dataFilePath(fileName()))

        val extraFileNames = findExtraFilesForTest(mainFile)

        val psiFile = myFixture.configureByFiles(*(listOf(mainFile.name) + extraFileNames).toTypedArray()).first()

        if ((myFixture.file as? KtFile)?.isScript() == true) {
            val dependenciesSource = object : BundledScriptConfigurationsSource(project, CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                override suspend fun updateModules(storage: MutableEntityStorage?) {
                    //do nothing because adding modules is not permitted in light tests
                }
            }
            project.registerExtension(SCRIPT_CONFIGURATIONS_SOURCES, dependenciesSource, testRootDisposable)

            val script = BaseScriptModel(psiFile.virtualFile)
            runWithModalProgressBlocking(project, "Testing") {
                dependenciesSource.updateDependenciesAndCreateModules(setOf(script))
            }
        }
        super.doTest(path)
    }
}