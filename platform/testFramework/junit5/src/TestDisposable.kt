// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.TestDisposableExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Injects a test [disposable][com.intellij.openapi.Disposable] to a field or a parameter in a test.
 * The new disposable instance is created before each test and disposed of after each test.
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
