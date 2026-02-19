// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.util.registry.Registry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets the [value] for the Registry [key],
 * and reverts to the previous value after running the test.
 *
 * Only Boolean keys are currently supported.
 * TODO change the [value] type to [String] when needed.
 *
 * For JUnit 3, call the
 * [RegistryValue.setValue][com.intellij.openapi.util.registry.RegistryValue.setValue]
 * variants with the additional Disposable argument.
 *
 * For JUnit 5, use [com.intellij.testFramework.junit5.RegistryKeyExtension].
 */
class RegistryKeyRule(private val key: String, private val value: Boolean) : TestRule {

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val registryValue = Registry.get(key)
        val previous = registryValue.asBoolean()
        registryValue.setValue(value)
        try {
          base.evaluate()
        }
        finally {
          registryValue.setValue(previous)
        }
      }
    }
  }
}
