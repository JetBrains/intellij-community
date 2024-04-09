// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import java.io.File

abstract class K2CreateFunctionFromUsageTest : LightIntentionActionTestCase() {
    override fun getTestDataPath(): String {
        return TestMetadataUtil.getTestDataPath(javaClass) + "/idea/tests/testData/quickfix/createFromUsage/createFunction"
    }

    override fun getRelativeBasePath():String {
        return StringUtil.substringAfter(javaClass.name, "$")!!
            .split("$")
            .joinToString(separator = "/") { PlatformTestUtil.lowercaseFirstLetter(it, true) }
    }

    override fun getFileSuffix(fileName: String): String? {
        return if ("""^(\w+)\.((before\.Main\.\w+)|(test))$""".toRegex().matchEntire(fileName) != null) fileName // multi-file test
        else if (fileName.contains(".after") || fileName.contains(".before.") ) null // after files
        else if (!fileName.contains('.')) return null  // looks like a directory
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
        val singleFileTest:AbstractK2QuickFixTest = object : AbstractK2QuickFixTest() {
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

    /**
     * Class names correspond to the testData directories inside /idea/tests/testData/quickfix/createFromUsage/
     * E.g. test class [Call.Abstract] will find all test files inside `/idea/tests/testData/quickfix/createFromUsage/call/abstract` and execute corresponding tests on them
     */
    //class BinaryOperations : K2CreateFunctionFromUsageTest()
    class Call {
        class Abstract : K2CreateFunctionFromUsageTest()
        class Extension : K2CreateFunctionFromUsageTest()
        //class ExtensionByExtensionReceiver : K2CreateFunctionFromUsageTest()
        class Member : K2CreateFunctionFromUsageTest()
        class Simple : K2CreateFunctionFromUsageTest()
        //class TypeArguments : K2CreateFunctionFromUsageTest()
    }
    //class CallableReferences : K2CreateFunctionFromUsageTest()
    //class Component : K2CreateFunctionFromUsageTest()
    //class DelegateAccessors : K2CreateFunctionFromUsageTest()
    class FromJava : K2CreateFunctionFromUsageTest()
    class FromKotlinToJava : K2CreateFunctionFromUsageTest()
    //class Get : K2CreateFunctionFromUsageTest()
    //class HasNext : K2CreateFunctionFromUsageTest()
    //class Invoke : K2CreateFunctionFromUsageTest()
    //class Iterator : K2CreateFunctionFromUsageTest()
    //class Next : K2CreateFunctionFromUsageTest()
    //class Set : K2CreateFunctionFromUsageTest()
    //class UnaryOperations : K2CreateFunctionFromUsageTest()
}