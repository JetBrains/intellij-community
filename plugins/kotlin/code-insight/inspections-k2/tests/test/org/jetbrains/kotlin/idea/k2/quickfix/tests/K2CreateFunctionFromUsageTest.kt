// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
/**
 * Tests "Create member function"/"Create extension function"/"Create abstract function" fixes
 */
abstract class K2CreateFunctionFromUsageTest : K2AbstractCreateFromUsageTest("createFunction") {
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