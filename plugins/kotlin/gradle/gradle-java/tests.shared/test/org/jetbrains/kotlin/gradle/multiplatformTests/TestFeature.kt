// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import java.io.File

/**
 * Represents a single tweak/additional check of tests.
 *
 * Recommended usage:
 * 1. inherit [TestFeature]
 * Tip: use `object` for implementors
 *
 * 2. if your test feature provides some per-test configuration, create a mutable class
 * and pass it as [V]. Please, store all settings there and do not extend [KotlinMppTestsContext]
 *
 * 3. override needed lifetime hooks and implement actual behaviour of the test feature
 *
 * 4. if necessary, provide the DSL for configuring your test feature by adding extension
 * functions on [TestConfigurationDslScope]
 * Tip: scope all extension-functions to a named interface, and inherit that
 * interface in relevant test suites. This helps with the completion speed/relevance a lot
 *
 * 5. install the feature in the relevant test suite
 * Default: add to [AbstractKotlinMppGradleImportingTest.installedFeatures])
 *
 * See also [AbstractTestChecker] for simplified API for test features which just provide
 * additional test assertions/checks.
 */
interface TestFeature<V : Any> {
    /**
     * Provides a fresh configuration for this feature. It will be invoked for each test
     */
    fun createDefaultConfiguration(): V

    /**
     * Executed after the whole test-instance/fixture setup, but before any actual test-related
     * actions in IDEA VM or on disk are executed (i.e. before `configureByFiles`)
     *
     * Tip: use it to tweak the project (e.g. to add a new file to the project)
     */
    fun KotlinMppTestsContext.beforeTestExecution() {}

    /**
     * Executed after the test-related actions in IDEA VM or on disk are executed.
     */
    fun KotlinMppTestsContext.afterTestExecution() {}

    /**
     * Will be invoked on copying the project from testdata to the temporary directory.
     * For each file, it's [origin] in the testdata directory and the current state of
     * preprocessed [text] is passed. Returned string will be used instead of [origin]'s
     * text.
     *
     * Tip: use it for custom pre-processing of files (e.g. to remove some
     * custom testdata markup)
     */
    fun preprocessFile(origin: File, text: String): String? = null

    /**
     * Invoked after the project is loaded into IDE (e.g. VFS, editors, most services are available),
     * but before Gradle Sync is invoked.
     *
     * Tip: use it to tweak the configuration of IDEA services that affect import (e.g., setting
     * some Registry-key)
     */
    fun KotlinMppTestsContext.beforeImport() {}

    /**
     * Invoked after import
     *
     * Tip: use for checks on the imported project
     */
    fun KotlinMppTestsContext.afterImport() {}

    /**
     * If `false`, the test feature should be explicitly enabled via the DSL of
     * [org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.GeneralWorkspaceChecks]
     */
    fun isEnabledByDefault(): Boolean = true
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
    fun additionalSetUp() {}

    fun additionalTearDown() {}
}

/**
 * Simplified API to implement for cases when you just want to launch some
 * checks after the project is set up, imported and ready to be used.
 *
 * ATTENTION. [AbstractTestChecker] are configurable by DSL from
 * [org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.WorkspaceChecksDsl]
 * This means, in particular, that when you add a new checker, tests with `onlyChecker(something)`
 * **won't run it**!
 *
 * If you want to add some check that should be executed in all tests, use raw [TestFeature]
 */
abstract class AbstractTestChecker<V : Any> : TestFeature<V> {
    abstract fun KotlinMppTestsContext.check()

    final override fun KotlinMppTestsContext.afterImport() {
        check()
    }

    final override fun KotlinMppTestsContext.beforeTestExecution() {}
    final override fun KotlinMppTestsContext.afterTestExecution() {}
    final override fun KotlinMppTestsContext.beforeImport() {}
}
