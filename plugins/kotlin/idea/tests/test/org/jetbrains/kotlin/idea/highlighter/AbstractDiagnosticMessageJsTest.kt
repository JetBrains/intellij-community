// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.instance
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS.analyzeFiles
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.CompilerEnvironment
import java.io.File
import java.lang.reflect.Field

abstract class AbstractDiagnosticMessageJsTest : AbstractDiagnosticMessageTest() {
    override fun analyze(file: KtFile, languageVersionSettings: LanguageVersionSettings): AnalysisResult {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, myFixture.module.name)
            put(JSConfigurationKeys.LIBRARIES, jsStdlib())
            put(CommonConfigurationKeys.DISABLE_INLINE, true)
            this.languageVersionSettings = languageVersionSettings
        }

        return analyzeFiles(listOf(file), JsConfig(project, configuration, CompilerEnvironment))
    }

    override val testDataDirectory: File
        get() = File(IDEA_TEST_DATA_DIR, "diagnosticMessage/js")

    override fun getPlatformSpecificDiagnosticField(diagnosticName: String): Field? {
        return getFieldOrNull(ErrorsJs::class.java, diagnosticName)
    }

    private fun jsStdlib(): List<String> {
        val stdlibPath = instance.kotlinStdlibJs
        return listOf(stdlibPath.absolutePath)
    }
}