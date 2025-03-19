// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
/**
 * Tests "Create local variable" fixes
 */
abstract class K2CreateVariableFromUsageTest : K2AbstractCreateFromUsageTest("createVariable") {
    /**
     * Class names correspond to the testData directories inside [getTestDataPath]
     * E.g. test class [LocalVariable] will find all test files inside `[getTestDataPath]/localVariable` and execute corresponding tests on them
     */
    class LocalVariable : K2CreateVariableFromUsageTest()
    class Parameter : K2CreateVariableFromUsageTest()
    class PrimaryParameter : K2CreateVariableFromUsageTest()
    class Property : K2CreateVariableFromUsageTest() {
        class Abstract: K2CreateVariableFromUsageTest()
        class FieldFromJava: K2CreateVariableFromUsageTest()
        class Extension: K2CreateVariableFromUsageTest()
    }
}