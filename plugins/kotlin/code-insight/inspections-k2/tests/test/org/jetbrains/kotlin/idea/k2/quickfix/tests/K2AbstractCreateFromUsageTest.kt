// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests
 
/**
 * Base class for K2CreateXXXFromUsageTest.
 * Just pass correct `[testDataCreateFromUsagePath]` - a relative path inside `/createUsage/
 */
abstract class K2AbstractCreateFromUsageTest(private val testDataCreateFromUsagePath: String) : K2AbstractQuickFixTest("/idea/tests/testData/quickfix/createFromUsage/"+testDataCreateFromUsagePath) {
}