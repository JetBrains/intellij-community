// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures.application

import com.intellij.gradle.testFramework.fixtures.impl.GradleImmutableProjectHolder
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Application initializer for tests that use
 * [com.intellij.gradle.testFramework.fixtures.immutableProjectFixture].
 *
 * Such a project is shared by all tests of a class, so the leak listeners are adjusted to the
 * [org.junit.jupiter.api.BeforeAll] and [org.junit.jupiter.api.AfterAll] bounds, exactly as they are for
 * [GradleProjectTestApplication]. The shared project is closed before those listeners run, since a project that
 * is still open holds virtual file pointers that the trackers would report as leaked.
 *
 * The annotation also sets the scope of the shared project. A test class without it cannot open such a project.
 * A nested class shares the project of its outer class.
 *
 * @see com.intellij.testFramework.junit5.TestApplication
 */
@Target(AnnotationTarget.CLASS)
@ExtendWith(TestApplicationExtension::class)
// Registered before the eviction below, so that its afterAll runs after it: afterAll callbacks are invoked in
// reverse registration order.
@ExtendWith(GradleProjectTestApplicationLeakTracker::class)
@ExtendWith(GradleImmutableProjectEviction::class)
@TestFixtures
annotation class GradleImmutableProjectTestApplication

private class GradleImmutableProjectEviction : BeforeAllCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    GradleImmutableProjectHolder.install(context)
  }

  override fun afterAll(context: ExtensionContext) {
    GradleImmutableProjectHolder.uninstall(context)
  }
}
