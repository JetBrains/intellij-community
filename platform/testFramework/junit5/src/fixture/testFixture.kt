// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KProperty

/**
 * Main building block for fixtures.
 * The [initializer] shall provide logic about how to build and destroy a fixture.
 * Scoping and fixture dependencies are taken care of by the framework.
 *
 * Example:
 * ```
 * fun myFixture(
 *   pathFixture: TestFixture<Path>,
 *   params: MyFixtureParams,
 * ): TestFixture<MyFixture> = testFixture {
 *   val path = pathFixture.fixture()
 *   val fixture: MyFixture = buildFixture(path, params)
 *   initialized(fixture) {
 *     destroyFixture(fixture)
 *   }
 * }
 * ```
 */
@TestOnly
fun <T> testFixture(debugString: String = "", initializer: TestFixtureInitializer<T>): TestFixture<T> {
  return TestFixtureImpl(debugString, initializer)
}

sealed interface TestFixture<out T> {

  /**
   * Gets value from an initialized fixture.
   *
   * @throws IllegalStateException when called during initialization. Use [TestFixtureInitializer.R.init] instead.
   */
  fun get(): T

  /**
   * Used for implementing property delegates of read-only properties.
   *
   * Example:
   * ```
   * @TestFixtures
   * class MyTest {
   *   private val path by pathFixture(...)
   * }
   * ```
   */
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
}

sealed interface TestContext {

  /**
   * Eel this fixture runs on.
   * It is usually null (local), unless a test is parametrized with eel.
   * See [EelForFixturesProvider] implementors.
   */
  val eel: EelApi?

  /**
   * Unique test or container ID, for example [org.junit.jupiter.api.extension.ExtensionContext.getUniqueId]
   */
  val uniqueId: String

  /**
   * Display name for the current test or container, for example [org.junit.jupiter.api.extension.ExtensionContext.getDisplayName]
   */
  val testName: String

  /**
   * Returns the annotation with which a test or container is marked or null if there is none.
   */
  fun <T : Annotation> findAnnotation(clazz: Class<T>): T?
}

/**
 * Represents the business logic for building a fixture (or resource) and destroying it.
 */
fun interface TestFixtureInitializer<T> {

  /**
   * @param context [TestContext] which provides information about the test and allows to query annotations
   */
  @OverrideOnly
  suspend fun R<T>.initFixture(context: TestContext): InitializedTestFixture<T>

  sealed interface InitializedTestFixture<T>

  sealed interface R<T> {

    /**
     * Gets value from another fixture, initializes it if needed.
     * Marks current fixture as dependant => another fixture will be torn down only after the current.
     * This function enables convenient fixture composition.
     * This function can be called multiple times on the same or different fixtures.
     */
    suspend fun <T> TestFixture<T>.init(): T

    /**
     * Creates a result of fixture init, which is effectively a value and a tear-down callback.
     * This function prepares a return value for [TestFixtureInitializer.initFixture].
     */
    fun initialized(fixture: T, tearDown: suspend () -> Unit): InitializedTestFixture<T>
  }
}
