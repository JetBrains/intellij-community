// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
/**
 * Tests "Create member function"/"Create extension function"/"Create abstract function" fixes
 */
abstract class K2CreateClassFromUsageTest : K2AbstractCreateFromUsageTest("createClass") {
    /**
     * Class names correspond to the testData directories inside /idea/tests/testData/quickfix/createFromUsage/
     * E.g. test class [ReferenceExpression] will find all test files inside `/idea/tests/testData/quickfix/createFromUsage/createClass/referenceExpression` and execute corresponding tests on them
     */
    class AnnotationEntry : K2CreateClassFromUsageTest()
    class CallExpression : K2CreateClassFromUsageTest()
    class DelegationSpecifier : K2CreateClassFromUsageTest()
    class ImportDirective : K2CreateClassFromUsageTest() {
        // kt21515 test data directory is no longer applicable to K2 since "Access to this type by short name is deprecated, and soon is going to be removed. Please, add explicit qualifier or import" warning is no longer produced
    }
    class ReferenceExpression : K2CreateClassFromUsageTest()
    class TypeReference : K2CreateClassFromUsageTest()
}