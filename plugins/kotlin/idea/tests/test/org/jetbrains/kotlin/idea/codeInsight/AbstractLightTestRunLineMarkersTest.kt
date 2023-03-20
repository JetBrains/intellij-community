// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.idea.junit.AbstractKotlinTestFrameworkAdapter
import org.jetbrains.kotlin.idea.runConfigurations.jvm.KotlinDelegatingTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework

abstract class AbstractLightTestRunLineMarkersTest: AbstractLineMarkersTest() {
    fun doLightTest(path: String) {
        // to test LightTestFramework detection only
        // i.e. w/o fallback to LightClasses and TestFrameworks.detectFramework
        //
        // Note: it is known in some case it could be not detected and fallback to LC is expected via LIGHT_CLASS_FALLBACK
        doRunTest(false) {
            try {
                super.doTest(path) {}
            } catch (e: FileComparisonFailure) {
                val lines = e.actual.split("\n")
                e.message?.let { msg ->
                    val regex = "^${Regex.escapeReplacement(dataFile().name)}: missing \\((\\d+):".toRegex()
                    regex.findAll(msg).toList().takeIf { it.isNotEmpty() }?.forEach {
                        val lineNo = it.groupValues[1].toInt()
                        val line = lines[lineNo - 1]
                        // known fallback to LightClass-based TestFramework detection
                        if (!line.contains("LIGHT_CLASS_FALLBACK")) throw e
                    } ?: throw e
                } ?: throw e
            }
        }
    }

    fun doPureTest(path: String) {
        // to test ONLY LightClasses and TestFrameworks.detectFramework
        // i.e. the most correct and reliable impl of test framework detection
        doRunTest(true) {
            super.doTest(path) {}
        }
    }

    private fun doRunTest(lightClass: Boolean, task: () -> Unit) {
        ExtensionTestUtil.maskExtensions(
            KotlinTestFramework.EXTENSION_NAME,
            KotlinTestFramework.EXTENSION_NAME.extensionList.filter {
                val filter = it is KotlinDelegatingTestFramework
                if (lightClass) filter else !filter
            },
            testRootDisposable
        )

        ExtensionTestUtil.maskExtensions(
            TestFramework.EXTENSION_NAME,
            TestFramework.EXTENSION_NAME.extensionList.filter {
                !lightClass || it !is AbstractKotlinTestFrameworkAdapter
            },
            testRootDisposable
        )

        task()
    }

}