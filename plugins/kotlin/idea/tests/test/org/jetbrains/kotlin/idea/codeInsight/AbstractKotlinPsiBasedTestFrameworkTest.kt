// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.execution.RunManager
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.java.codeInsight.navigation.MockGradleRunConfiguration
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework

abstract class AbstractKotlinPsiBasedTestFrameworkTest : AbstractLineMarkersTest() {
    fun doPsiBasedTest(path: String) {
        // to test LightTestFramework detection only
        // i.e. w/o fallback to LightClasses and TestFrameworks.detectFramework
        //
        // Note: it is known in some case it could be not detected and fallback to LC is expected via LIGHT_CLASS_FALLBACK
        doRunTest(false) {
            try {
                super.doTest(path) {}
            } catch (e: AssertionError) {
                checkSuppressions("LIGHT_CLASS_FALLBACK", e)
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

    fun doTestWithGradleConfiguration(path: String) {
        val runManager = RunManager.getInstance(myFixture.project)
        val mockConfiguration = MockGradleRunConfiguration(myFixture.project, "runTest")
        val runnerAndConfigurationSettings = RunnerAndConfigurationSettingsImpl(runManager as RunManagerImpl, mockConfiguration)
        runManager.addConfiguration(runnerAndConfigurationSettings)
        runManager.selectedConfiguration = runnerAndConfigurationSettings
        try {
            super.doTest(path) {}
        } catch (e : AssertionError) {
            checkSuppressions("DISABLED_WITH_GRADLE_CONFIGURATION", e)
        }
        finally {
            runManager.removeConfiguration(runnerAndConfigurationSettings)
        }
    }

    private fun checkSuppressions(suppressionName : String, e: AssertionError) {
        if (e !is FileComparisonData) throw e
        val lines = e.actualStringPresentation.split("\n")
        e.message?.let { msg ->
            val regex = "^${Regex.escapeReplacement(dataFile().name)}: missing \\((\\d+):".toRegex()
            regex.findAll(msg).toList().takeIf { it.isNotEmpty() }?.forEach {
                val lineNo = it.groupValues[1].toInt()
                val line = lines[lineNo - 1]
                // known fallback to LightClass-based TestFramework detection
                if (!line.contains(suppressionName)) throw e
            } ?: throw e
        } ?: throw e
    }

    private fun doRunTest(lightClass: Boolean, task: () -> Unit) {
        val applicableTestFrameworks = TestFramework.EXTENSION_NAME.extensionList.filter {
            val filter = it is KotlinPsiBasedTestFramework
            if (lightClass) !filter else filter
        }

        maskExtensions(TestFramework.EXTENSION_NAME, applicableTestFrameworks, testRootDisposable)
        task()
    }

}