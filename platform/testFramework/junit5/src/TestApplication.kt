// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Isolated

/**
 * Initializes [shared application instance][com.intellij.openapi.application.ApplicationManager.getApplication]
 * once before all tests are run.
 * The application is disposed together with the [root][org.junit.jupiter.api.extension.ExtensionContext.getRoot] context,
 * i.e., after all tests were run.
 *
 * A class with this annotation is [Isolated]: in a run with parallel classes, no other test runs at the same time as
 * its tests. Its tests can change the global state of the shared application, and the checks of the whole JVM after
 * each test, such as the check of leaked threads, stay valid. A run without parallel execution ignores the annotation.
 *
 * @see com.intellij.testFramework.junit5.showcase.JUnit5ApplicationTest
 * @see RegistryKey for setting registry values in tests
 * @see TestDisposable for injecting test-scoped disposables
 * @see RunInEdt for running tests in EDT
 * @see SystemProperty for setting system properties in tests
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  TestApplicationExtension::class,
  TestApplicationLeakTrackerExtension::class,
)
@TestFixtures
@Isolated
annotation class TestApplication
