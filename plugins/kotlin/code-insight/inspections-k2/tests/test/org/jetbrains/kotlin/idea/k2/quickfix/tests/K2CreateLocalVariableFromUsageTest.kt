// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
import org.jetbrains.kotlin.idea.test.TestMetadataUtil

/**
 * Tests "Create local variable" fixes
 */
abstract class K2CreateLocalVariableFromUsageTest : K2AbstractCreateFromUsageTest() {
    override fun getTestDataPath(): String {
        return TestMetadataUtil.getTestDataPath(javaClass) + "/idea/tests/testData/quickfix/createFromUsage/createVariable"
    }

    /**
     * Class names correspond to the testData directories inside [getTestDataPath]
     * E.g. test class [Call.Abstract] will find all test files inside `[getTestDataPath]/call/abstract` and execute corresponding tests on them
     */
    class LocalVariable : K2CreateLocalVariableFromUsageTest()
}