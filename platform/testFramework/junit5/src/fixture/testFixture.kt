// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

/**
 * Main building block for fixtures.
 * The [initializer] shall provide logic about how to build and destroy a fixture.
 * Scoping and fixture dependencies are taken care of be the framework.
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
fun <T> testFixture(debugString: String = "", initializer: TestFixtureInitializer<T>): TestFixture<T> {
  return TestFixtureImpl(debugString, initializer)
}

sealed interface TestFixture<T> {

  /**
   * Gets value from an initialized fixture.
   *
   * @throws IllegalStateException when called during initialization. Use [TestFixtureInitializer.R.init] instead.
   */
  fun get(): T
}

/**
 * Represents the business logic for building a fixture (or resource) and destroying it.
 */
fun interface TestFixtureInitializer<T> {

  /**
   * @param uniqueId unique test or container ID, same as [org.junit.jupiter.api.extension.ExtensionContext.getUniqueId]
   *
   * TODO consider passing whole ExtensionContext here
   */
  suspend fun R<T>.initFixture(uniqueId: String): InitializedTestFixture<T>

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
