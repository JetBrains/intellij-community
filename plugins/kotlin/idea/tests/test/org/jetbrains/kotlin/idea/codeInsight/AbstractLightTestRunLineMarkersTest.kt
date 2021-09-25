// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.kotlin.idea.platform.testintegration.LightTestFramework

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
                    val regex = "^${Regex.escapeReplacement(testDataFile().name)}: missing \\((\\d+):".toRegex()
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
        val extensionList = LightTestFramework.EXTENSION_NAME.extensionList
        ExtensionTestUtil.maskExtensions(
            LightTestFramework.EXTENSION_NAME,
            extensionList.filter {
                val filter = it.name == "LightClass"
                if (lightClass) filter else !filter
             },
            testRootDisposable
        )
        task()
    }

}