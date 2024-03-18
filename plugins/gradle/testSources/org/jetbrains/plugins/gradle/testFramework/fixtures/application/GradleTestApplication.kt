// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @see com.intellij.testFramework.junit5.TestApplication
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  GradleTestApplicationExtension::class,
  GradleTestApplicationLeakTrackerExtension::class,
)
annotation class GradleTestApplication {
}