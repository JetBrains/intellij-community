// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.platform.testFramework.junit5.eel.params.impl.junit5.EelInterceptor
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Mark test class:
 * ```kotlin
 * @EelTestApplication
 * class MyTest {
 *     private val projectFixture = projectFixture() // these fixtures are
 *     private val tempDir = tempPathFixture() // are also sit on en eel, but only instance-level, not project level
 *   @ParametrizedTest
 *   @EelSource // With Junit5Pioneer annotate parameter
 *   fun myTest(eh:EelHolder) {
 *   eh.eel
 *   }
 * }
 * ```
 *
 * Warning: You need to provide a special vm option, most likely
 * ```
 * -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
 * ```
 * Run a test, and failure will report an option name.
 *
 * System tries to run your test against at least one remote (ijent-based) eel.
 * You need to have providers (i.e. `intellij.platform.ijent.testFramework` in a classpath) to do that.
 *
 * If your particular test doesn't need remote eels for a certain OS, use [osesMayNotHaveRemoteEels].
 *
 * The following test will fail if no remote Eel available on any OS but Windows:
 * ```kotlin
 * @TestApplicationWithEel(osesMayNotHaveRemoteEels=[OS.WINDOWS])
 * ```
 * Do not use this option unless you are absolutely sure.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  EelInterceptor::class,
)
@TestApplication
annotation class TestApplicationWithEel(vararg val osesMayNotHaveRemoteEels: OS)