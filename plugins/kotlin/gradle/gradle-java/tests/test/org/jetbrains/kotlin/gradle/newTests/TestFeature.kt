// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import java.io.File

interface TestFeature<V : Any> {
    fun createDefaultConfiguration(): V

    fun KotlinMppTestsContext.beforeTestExecution() { }

    fun KotlinMppTestsContext.beforeImport() { }

    fun preprocessFile(origin: File, text: String): String? = null

    fun KotlinMppTestsContext.afterImport() { }
}

/**
 * Provides an additional hooks into setUp and tearDown
 *
 * Important properties/guarantees:
 * - those features can be configured only in `installedFeatures` directly in the test runner
 *   (see [AbstractKotlinMppGradleImportingTest.installedFeatures]), **not** in the
 *   `TestConfiguration`-DSL block. This is because TestConfiguration is instantiated at
 *   the test execution phase (`doTest`) that happens after setUp
 *
 * - [additionalSetUp] is executed after the main JUnit's setUp executed, order with other
 *   'before'-methods coming from JUnit rules is not defined (please avoid mixing them)
 *
 * - [additionalTearDown] is executed before the JUnit's tearDown is executed, order with other
 *   'after'-methods coming from JUnit rules is not defined (please avoid mixing them)
 *
 * - [additionalTearDown] is guaranteed to be executed if respective [additionalSetUp] was executed
 *   (difference from [TestFeature.afterImport] that happens effectively at the same stage of test's lifecyle,
 *   but doesn't have that guarantee)
 */
interface TestFeatureWithSetUpTearDown<V : Any> : TestFeature<V> {
    fun additionalSetUp() { }

    fun additionalTearDown() {}
}

/**
 * Simplified API to implement for cases when you just want to launch some
 * checks after the project is set up, imported and ready to be used
 */
abstract class AbstractTestChecker<V : Any> : TestFeature<V> {
    abstract fun KotlinMppTestsContext.check(additionalTestClassifier: String? = null)

    final override fun KotlinMppTestsContext.afterImport() {
        check()
    }

    final override fun KotlinMppTestsContext.beforeTestExecution() { }
    final override fun KotlinMppTestsContext.beforeImport() { }
}
