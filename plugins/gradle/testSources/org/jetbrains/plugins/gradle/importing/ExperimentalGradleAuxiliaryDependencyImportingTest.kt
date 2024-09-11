// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.junit.Assume

class ExperimentalGradleAuxiliaryDependencyImportingTest : GradleAuxiliaryDependencyImportingTest() {

  private companion object {
    private const val EXPERIMENTAL_RESOLVER_PROPERTY_KEY = "-Didea.experimental.gradle.dependency.resolver=true"
  }

  override fun setUp() {
    Assume.assumeFalse(
      "The test can be executed only with Gradle 7.3+.",
      isGradleOlderThan("7.3")
    )
    super.setUp()
  }

  override fun configureGradleVmOptions(options: MutableSet<String>) {
    super.configureGradleVmOptions(options)
    options.add(EXPERIMENTAL_RESOLVER_PROPERTY_KEY)
  }
}