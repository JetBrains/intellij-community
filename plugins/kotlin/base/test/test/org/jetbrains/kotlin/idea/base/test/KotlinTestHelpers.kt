// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import junit.framework.TestCase
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object KotlinTestHelpers {

    fun getTestRootPath(testClass: Class<*>): Path {
        var current = testClass
        while (true) {
            // @TestRoot should be defined on a top-level class
            current = current.enclosingClass ?: break
        }

        val testRootAnnotation = current.getAnnotation(TestRoot::class.java)
            ?: throw AssertionError("@${TestRoot::class.java.name} annotation must be defined on a class '${current.name}'")

        return KotlinRoot.PATH.resolve(testRootAnnotation.value)
    }

    fun assertEqualsToPath(expectedPath: Path, actual: String) {
        assertEqualsToPath(expectedPath, actual, { it }) { "Expected file content differs from the actual result" }
    }

    data class DoesEqual(val yes: Boolean, val processedExpected: String, val processedActual: String)

    fun doesEqualsToPath(expectedPath: Path, actual: String, sanitizer: (String) -> String): DoesEqual {
        if (!expectedPath.exists()) {
            expectedPath.writeText(actual)
            TestCase.fail("File didn't exist. New file was created (${expectedPath.absolutePathString()}).")
        }

        fun process(output: String): String {
            return output
                .trim()
                .let(StringUtil::convertLineSeparators)
                .trimTrailingWhitespacesAndAddNewlineAtEOF()
                .let(sanitizer)
        }

        val processedExpected = process(expectedPath.readText())
        val processedActual = process(actual)
        return DoesEqual(processedExpected == processedActual, processedExpected, processedActual)
    }

    fun assertEqualsToPath(expectedPath: Path, actual: String, sanitizer: (String) -> String, message: () -> String) {
        val equalsToPath = doesEqualsToPath(expectedPath, actual, sanitizer)
        if (!equalsToPath.yes) {
            throw FileComparisonFailedError(
                message(),
                equalsToPath.processedExpected,
                equalsToPath.processedActual,
                expectedPath.absolutePathString()
            )
        }
    }

    fun registerChooserInterceptor(
        parent: Disposable,
        chooseOption: (options: List<String>) -> String = { it.first() },
    ) {
        val chooserInterceptor = ChooserInterceptor(
            /* expectedOptions = */ null,
            /* pattern = */ "(\n|.)*",
            /* chooseOption = */ chooseOption,
        )

        UiInterceptors.registerPossible(parent, chooserInterceptor)
    }
}
