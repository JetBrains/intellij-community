// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Initializes [shared application instance][com.intellij.openapi.application.ApplicationManager.getApplication]
 * once before all tests are run.
 * The application is disposed together with the [root][org.junit.jupiter.api.extension.ExtensionContext.getRoot] context,
 * i.e., after all tests were run.
 *
 * @see com.intellij.testFramework.junit5.showcase.JUnit5ApplicationTest
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  TestApplicationExtension::class,
  TestApplicationLeakTrackerExtension::class,
)
@TestFixtures
annotation class TestApplication
