// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * When applied to a test class, this annotation ensures that
 * the [TestFixture] fields are initialized and torn down.
 * - Instance-level fixtures are initialized and torn down for each test
 * similarly to [@BeforeEach/@AfterEach][org.junit.jupiter.api.BeforeEach].
 * - Class-level fixtures (in static fields) are initialized before all tests and torn down after all tests in the class
 * similarly to [@BeforeAll/@AfterAll][org.junit.jupiter.api.BeforeAll].
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  TestFixtureExtension::class,
)
annotation class TestFixtures
