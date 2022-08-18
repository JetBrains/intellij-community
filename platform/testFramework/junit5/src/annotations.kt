// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Instructs the framework to run methods in EDT.
 * If [allMethods] is set to `true` (default), then all test class methods will be run in EDT, including lifecycle methods.
 * If [allMethods] is set to `false`, then methods annotated with [RunMethodInEdt] will be run in EDT.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@Inherited
@ExtendWith(EdtInterceptorExtension::class)
annotation class RunInEdt(val allMethods: Boolean = true)

/**
 * Instructs the framework to run a single method in EDT. [RunInEdt] is required on the class level for this annotation to be picked up.
 */
@TestOnly
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
annotation class RunMethodInEdt

/**
 * Injects a test [disposable][com.intellij.openapi.Disposable] to a field or a parameter in a test.
 * The new disposable instance is created before each test and disposed after each test.
 *
 * Example:
 * ```kotlin
 * class MyTest {
 *
 *   // a disposable will be created for each test
 *   @TestDisposable
 *   lateinit var disposable
 *
 *   @Test
 *   fun test1() {}
 *
 *   @Test
 *   fun test2() {}
 * }
 *
 * class MyTest2 {
 *
 *   @Test
 *   fun test1() {}
 *
 *   // a disposable will be created only for this test
 *   @Test
 *   fun test2(@TestDisposable disposable: Disposable) {}
 * }
 * ```
 * @see com.intellij.testFramework.junit5.showcase.JUnit5DisposableTest
 */
@TestOnly
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@ExtendWith(TestDisposableExtension::class)
annotation class TestDisposable

/**
 * Initializes [shared application instance][com.intellij.openapi.application.ApplicationManager.getApplication]
 * once before all tests are run.
 * The application is disposed together with the [root][org.junit.jupiter.api.extension.ExtensionContext.getRoot] context,
 * i.e. after all tests were run.
 *
 * @see com.intellij.testFramework.junit5.showcase.JUnit5ApplicationTest
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  TestApplicationExtension::class,
  TestApplicationLeakTrackerExtension::class,
)
annotation class TestApplication
