// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import java.io.File

/**
 * Base class for quick fix tests.
 * Automatically loads the test base directory based on the test name.
 * E.g. `class MySomething extends K2AbstractQuickFixTest` would load test data from the relative directory "mySomething".
 * Then it determines if the test data is multi-file or single-file and creates and starts AbstractK2MultiFileQuickFixTest or AbstractK2QuickFixTest respectively.
 */
abstract class K2AbstractQuickFixTest(private val relativePath: String) : LightIntentionActionTestCase() {
    override fun getTestDataPath(): String {
        return TestMetadataUtil.getTestDataPath(javaClass) + relativePath
    }

    override fun getRelativeBasePath():String {
        return StringUtil.substringAfter(javaClass.name, "$")!!
            .split("$")
            .joinToString(separator = "/") { PlatformTestUtil.lowercaseFirstLetter(it, true) }
    }

    override fun getFileSuffix(fileName: String): String? {
        return if ("""^(\w+)\.((before\.Main\.\w+)|(test))$""".toRegex().matchEntire(fileName) != null) fileName // multi-file test
        else if (fileName.contains(".after") || fileName.contains(".before.") ) null // after files
        else if (!fileName.contains('.')) null  // looks like a directory
        else if (fileName.equals(".inspection") || fileName.equals(".k2Inspection")) null // not a test
        else fileName // single-file test
    }

    override fun getBaseName(fileAfterSuffix: String): String? {
        return null
    }

    override fun isRunInCommand(): Boolean {
        return false
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk10()
    }

    override fun doSingleTest(fileSuffix: String, testDataPath: String) {
        val filePath = "$testDataPath/$relativeBasePath/$fileSuffix"
        val test: LightJavaCodeInsightFixtureTestCase
        val singleFileTest = object : AbstractK2QuickFixTest() {

            override val pluginMode: KotlinPluginMode
                get() = KotlinPluginMode.K2

            override fun getProjectDescriptor(): LightProjectDescriptor {
                return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk10()
            }

            override val testDataDirectory: File
                get() = File("$testDataPath/$relativeBasePath")

            override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
                KotlinTestUtils.runTest(this::doTest, this, filePath)
            }

            override fun fileName(): String {
                return fileSuffix
            }

            override fun setUp() {
                super.setUp()
                myFixture.setTestDataPath("$testDataPath/$relativeBasePath")
            }
        }
        val afterFileName = singleFileTest.getAfterFileName(fileSuffix)
        if (afterFileName != fileSuffix && File("$testDataPath/$relativeBasePath/$afterFileName").exists()) {
            test = singleFileTest
        }
        else {
            test = object : AbstractK2MultiFileQuickFixTest() {

                override val pluginMode: KotlinPluginMode
                    get() = KotlinPluginMode.K2

                override fun getProjectDescriptor(): LightProjectDescriptor {
                    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk10()
                }

                override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
                    KotlinTestUtils.runTest(this::doTestWithExtraFile, this, filePath)
                }

                override fun fileName(): String {
                    return fileSuffix
                }

                override fun setUp() {
                    super.setUp()
                    myFixture.setTestDataPath("$testDataPath/$relativeBasePath")
                }
            }
        }
        test.name = fileSuffix
        test.runBare()
    }

    // do not create another project, since we instantiate and run test cases manually in doSingleTest
    override fun setUp() {
    }

    override fun tearDown() {
    }
}