// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

class LegacyGradleAuxiliaryDependencyImportingTest : GradleAuxiliaryDependencyImportingTest() {

  private companion object {
    private const val LEGACY_RESOLVER_PROPERTY_KEY = "-Didea.gradle.daemon.legacy.dependency.resolver=true"
  }

  override fun configureGradleVmOptions(options: MutableSet<String>) {
    super.configureGradleVmOptions(options)
    options.add(LEGACY_RESOLVER_PROPERTY_KEY)
  }
}