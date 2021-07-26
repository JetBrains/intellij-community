// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.formatHtml.formatHtml
import org.jetbrains.kotlin.idea.project.withLanguageVersionSettings
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.io.File
import java.lang.reflect.Field
import java.util.*

abstract class AbstractDiagnosticMessageTest : KotlinLightCodeInsightFixtureTestCase() {
    private enum class MessageType(val directive: String, val extension: String) {
        TEXT("TEXT", "txt"), HTML("HTML", "html");
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    override val testDataDirectory: File
        get() = File(IDEA_TEST_DATA_DIR, "diagnosticMessage")

    protected fun languageVersionSettings(fileData: String): LanguageVersionSettings {
        val specificFeatures: Map<LanguageFeature, LanguageFeature.State> = parseLanguageFeatures(fileData)
        val explicitLanguageVersion = InTextDirectivesUtils.findStringWithPrefixes(fileData, "// LANGUAGE_VERSION:")

        val version = explicitLanguageVersion?.let { LanguageVersion.fromVersionString(it) }
        val languageVersion = if (explicitLanguageVersion == null) LanguageVersionSettingsImpl.DEFAULT.languageVersion else version!!

        return LanguageVersionSettingsImpl(languageVersion, LanguageVersionSettingsImpl.DEFAULT.apiVersion, emptyMap(), specificFeatures)
    }

    open fun analyze(file: KtFile, languageVersionSettings: LanguageVersionSettings): AnalysisResult {
        val module = file.module ?: error("Module is not found for file $file")

        return module.withLanguageVersionSettings(languageVersionSettings) {
            file.analyzeWithAllCompilerChecks()
        }
    }

    fun doTest(filePath: String) {
        val file = File(filePath)
        val fileData = KotlinTestUtils.doLoadFile(file)
        val directives = KotlinTestUtils.parseDirectives(fileData)

        val diagnosticNumber = getDiagnosticNumber(directives)
        val diagnosticFactories = getDiagnosticFactories(directives)
        val messageType = getMessageTypeDirective(directives)

        val ktFile = myFixture.configureByFile(filePath) as KtFile

        val languageVersionSettings = languageVersionSettings(fileData)
        val analysisResult = analyze(ktFile, languageVersionSettings)
        val bindingContext = analysisResult.bindingContext
        val diagnostics = bindingContext.diagnostics.all().filter { diagnosticFactories.contains(it.factory) }
        assertEquals("Expected diagnostics number mismatch:", diagnosticNumber, diagnostics.size)

        val testName = FileUtil.getNameWithoutExtension(file.name)
        for ((index, diagnostic) in diagnostics.withIndex()) {
            var readableDiagnosticText: String
            var extension: String
            if (messageType != MessageType.TEXT && IdeErrorMessages.hasIdeSpecificMessage(diagnostic)) {
                readableDiagnosticText = formatHtml(IdeErrorMessages.render(diagnostic))
                extension = MessageType.HTML.extension
            } else {
                readableDiagnosticText = DefaultErrorMessages.render(diagnostic)
                extension = MessageType.TEXT.extension
            }

            val errorMessageFileName = testName + (index + 1)
            val outputFile = File(testDataDirectory, "$errorMessageFileName.$extension")
            val actualText = "<!-- $errorMessageFileName -->\n$readableDiagnosticText"
            assertSameLinesWithFile(outputFile.path, actualText)
        }
    }

    private fun getDiagnosticFactories(directives: Directives): Set<DiagnosticFactory<*>?> {
        val diagnosticsData = directives[DIAGNOSTICS_DIRECTIVE] ?: error("$DIAGNOSTICS_DIRECTIVE should be present.")
        val diagnosticFactories: MutableSet<DiagnosticFactory<*>?> = HashSet()
        val diagnostics = diagnosticsData.split(" ".toRegex()).toTypedArray()
        for (diagnosticName in diagnostics) {
            val diagnostic = getDiagnostic(diagnosticName)
            assert(diagnostic is DiagnosticFactory<*>) { "Can't load diagnostic factory for $diagnosticName" }
            diagnosticFactories.add(diagnostic as DiagnosticFactory<*>?)
        }
        return diagnosticFactories
    }

    private fun getDiagnostic(diagnosticName: String): Any? {
        val field = getPlatformSpecificDiagnosticField(diagnosticName)
            ?: getFieldOrNull(Errors::class.java, diagnosticName)
            ?: return null

        return try {
            field[null]
        } catch (e: IllegalAccessException) {
            null
        }
    }

    protected open fun getPlatformSpecificDiagnosticField(diagnosticName: String): Field? {
        return getFieldOrNull(ErrorsJvm::class.java, diagnosticName)
    }

    companion object {
        private const val DIAGNOSTICS_NUMBER_DIRECTIVE = "DIAGNOSTICS_NUMBER"
        private const val DIAGNOSTICS_DIRECTIVE = "DIAGNOSTICS"
        private const val MESSAGE_TYPE_DIRECTIVE = "MESSAGE_TYPE"

        fun getFieldOrNull(kind: Class<*>, field: String): Field? {
            return try {
                kind.getField(field)
            } catch (e: NoSuchFieldException) {
                null
            }
        }

        private fun parseLanguageFeatures(fileText: String): Map<LanguageFeature, LanguageFeature.State> {
            val directives = InTextDirectivesUtils.findListWithPrefixes(fileText, "// !LANGUAGE:")
            val result: MutableMap<LanguageFeature, LanguageFeature.State> = EnumMap(LanguageFeature::class.java)
            for (directive in directives) {
                val state = when (directive[0]) {
                    '+' -> LanguageFeature.State.ENABLED
                    '-' -> LanguageFeature.State.DISABLED
                    else -> continue
                }
                val feature = LanguageFeature.fromString(directive.substring(1)) ?: continue
                result[feature] = state
            }
            return result
        }

        private fun getDiagnosticNumber(directives: Directives): Int {
            val diagnosticsNumber = directives[DIAGNOSTICS_NUMBER_DIRECTIVE] ?: error("$DIAGNOSTICS_NUMBER_DIRECTIVE should be present.")
            return try {
                diagnosticsNumber.toInt()
            } catch (e: NumberFormatException) {
                throw AssertionError("$DIAGNOSTICS_NUMBER_DIRECTIVE should contain number as its value.")
            }
        }

        private fun getMessageTypeDirective(directives: Directives): MessageType? {
            val messageType = directives[MESSAGE_TYPE_DIRECTIVE] ?: return null

            return try {
                MessageType.valueOf(messageType)
            } catch (e: IllegalArgumentException) {
                throw AssertionError(
                    "$MESSAGE_TYPE_DIRECTIVE should be ${MessageType.TEXT.directive} " +
                            "or ${MessageType.HTML.directive}. But was: \"$messageType\"."
                )
            }
        }
    }
}