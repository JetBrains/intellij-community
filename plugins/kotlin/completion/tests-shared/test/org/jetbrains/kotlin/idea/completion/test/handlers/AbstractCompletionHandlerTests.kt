// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.runTestIfNotDisabledByFileDirective
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.addCharacterCodingException
import org.jetbrains.kotlin.idea.completion.test.configureByFilesWithSuffixes
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.test.utils.withExtension
import org.jetbrains.kotlin.utils.addToStdlib.indexOfOrNull

abstract class AbstractCompletionHandlerTest(private val defaultCompletionType: CompletionType) : CompletionHandlerTestBase() {
    companion object {
        const val INVOCATION_COUNT_PREFIX = "INVOCATION_COUNT:"
        const val LOOKUP_STRING_PREFIX = "ELEMENT:"
        const val ELEMENT_TEXT_PREFIX = "ELEMENT_TEXT:"
        const val TAIL_TEXT_PREFIX = "TAIL_TEXT:"
        const val USE_EXPENSIVE_RENDERER = "// USE_EXPENSIVE_RENDERER"
        const val COMPLETION_CHAR_PREFIX = "CHAR:"
        const val COMPLETION_CHARS_PREFIX = "CHARS:"
        const val CODE_STYLE_SETTING_PREFIX = "CODE_STYLE_SETTING:"
        const val RETAIN_OVERRIDE_ANNOTATION_DIRECTIVE = "RETAIN_OVERRIDE_ANNOTATIONS:"
    }

    protected open fun doTest(testPath: String) {
        if (isFirPlugin) {
            runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.IGNORE_K2, ".after") {
                test(testPath)
                val originalTestFile = dataFile()
                val extension = originalTestFile.extension
                val k2Extension = IgnoreTests.FileExtension.FIR
                val originalAfterFile = originalTestFile.withExtension("$extension.after")
                val firAfterFile = originalTestFile.withExtension("$k2Extension.$extension.after")
                IgnoreTests.cleanUpIdenticalK2TestFile(
                    originalTestFile,
                    k2Extension,
                    additionalFileToMarkFirIdentical = originalAfterFile,
                    additionalFileToDeleteIfIdentical = firAfterFile,
                    additionalFilesToCompare = listOf(originalAfterFile to firAfterFile)
                )
            }
        } else {
            runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.IGNORE_K1, ".after") {
                test(testPath)
            }
        }
    }

    private fun test(testPath: String) {
        val testFile = dataFile()
        setUpFixture(testFile.name)
        try {
            configureCodeStyleAndRun(project) {
                val fileText = FileUtil.loadFile(testFile)
                val annotationsToRetain = InTextDirectivesUtils.findStringWithPrefixes(fileText, RETAIN_OVERRIDE_ANNOTATION_DIRECTIVE)
                if (annotationsToRetain != null) {
                    OverrideImplementsAnnotationsFilter.EP_NAME.point.registerExtension(
                        OverrideImplementsAnnotationsFilter { annotationsToRetain.split(',').toTypedArray() },
                        testRootDisposable
                    )
                }
                withCustomCompilerOptions(fileText, project, module) {
                    assertTrue("\"<caret>\" is missing in file \"$testFile\"", fileText.contains("<caret>"))

                    val invocationCount = InTextDirectivesUtils.getPrefixedInt(fileText, INVOCATION_COUNT_PREFIX) ?: 1
                    val lookupString = InTextDirectivesUtils.findStringWithPrefixes(fileText, LOOKUP_STRING_PREFIX)
                    val itemText = InTextDirectivesUtils.findStringWithPrefixes(fileText, ELEMENT_TEXT_PREFIX)
                    val tailText = InTextDirectivesUtils.findStringWithPrefixes(fileText, TAIL_TEXT_PREFIX)
                    val useExpensiveRenderer = InTextDirectivesUtils.isDirectiveDefined(fileText, USE_EXPENSIVE_RENDERER)
                    val completionChars = completionChars(fileText)

                    val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType

                    val codeStyleSettings = CodeStyle.getSettings(file)
                    val kotlinStyleSettings = codeStyleSettings.kotlinCustomSettings
                    val commonStyleSettings = codeStyleSettings.kotlinCommonSettings
                    for (line in InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, CODE_STYLE_SETTING_PREFIX)) {
                        val index = line.indexOfOrNull('=') ?: error("Invalid code style setting '$line': '=' expected")
                        val settingName = line.substring(0, index).trim()
                        val settingValue = line.substring(index + 1).trim()
                        val (field, settings) = try {
                            kotlinStyleSettings::class.java.getField(settingName) to kotlinStyleSettings
                        } catch (e: NoSuchFieldException) {
                            commonStyleSettings::class.java.getField(settingName) to commonStyleSettings
                        }

                        when (field.type.name) {
                            "boolean" -> field.setBoolean(settings, settingValue.toBoolean())
                            "int" -> field.setInt(settings, settingValue.toInt())
                            else -> error("Unsupported setting type: ${field.type}")
                        }
                    }

                    doTestWithTextLoaded(
                        fileText,
                        myFixture,
                        completionType,
                        invocationCount,
                        lookupString,
                        itemText,
                        tailText,
                        completionChars,
                        testFile.name + ".after",
                        useExpensiveRenderer = useExpensiveRenderer
                    )
                }
            }
        } finally {
            tearDownFixture()
        }
    }

    protected open fun setUpFixture(testPath: String) {
        // this class is missing in mockJDK-1.8
        fixture.addCharacterCodingException()

        fixture.configureByFilesWithSuffixes(dataFile(), testDataDirectory, ".dependency", ".dependency.1", ".dependency.2")
    }

    protected open fun tearDownFixture() {

    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}

abstract class AbstractBasicCompletionHandlerTest : AbstractCompletionHandlerTest(CompletionType.BASIC)

abstract class AbstractSmartCompletionHandlerTest : AbstractCompletionHandlerTest(CompletionType.SMART)

abstract class AbstractCompletionCharFilterTest : AbstractCompletionHandlerTest(CompletionType.BASIC)

abstract class AbstractKeywordCompletionHandlerTest : AbstractCompletionHandlerTest(CompletionType.BASIC)
