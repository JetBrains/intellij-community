// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.junit.runner.Description
import java.io.File

/**
 * Rule which provides a corresponding test data directory via [testDataDirectory]
 * according to the following logic:
 *
 * - each test method has form like 'test<TestName>' or '<testName>'
 * - test directory for a <TestName> is expected to be found at [IDEA_TEST_DATA_DIR]/$testDataDirName/<testName>
 *   (note decapitalizing the first letter in <TestName> if necessary>
 */
class TestDataDirectoryService(
    // relative to [IDEA_TEST_DATA_DIR]
    private val testDataDirRootPath: String
) : KotlinBeforeAfterTestRuleWithDescription {
    private var testMethodName: String? = null

    fun testDataDirectory(): File {
        val testMethodName = requireNotNull(testMethodName) {
            "Test method name is null, probably 'before' for this test isn't executed yet"
        }
        val testName = if (testMethodName.startsWith("test")) 
            testMethodName.removePrefix("test").decapitalizeAsciiOnly()
        else
            testMethodName
        
        val allTestDataRootDir = IDEA_TEST_DATA_DIR.resolve(testDataDirRootPath)
        val thisTestData = File(allTestDataRootDir, testName)
        
        require(thisTestData.exists()) {
            """Can't find test data directory
                | testMethodName = $testMethodName
                | testName = $testName
                | IDEA_TEST_DATA_DIR = ${IDEA_TEST_DATA_DIR.canonicalPath}
                | testDataDirRootPath = ${testDataDirRootPath}
                | allTestDataRootDir = ${allTestDataRootDir.canonicalPath}
                | thisTestDataDir = ${thisTestData.canonicalPath}  
            """.trimMargin()
        }

        return thisTestData
    }

    override fun before(description: Description) {
        testMethodName = description.methodName
    }
}
