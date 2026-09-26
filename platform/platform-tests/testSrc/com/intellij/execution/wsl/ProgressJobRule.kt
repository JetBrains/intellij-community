// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.time.Duration

/**
 * Sets a [kotlinx.coroutines.Job] into [com.intellij.concurrency.currentThreadContext] for every test.
 */
class ProgressJobRule(private val coroutineTimeout: Duration) : TestRule {
  constructor() : this(Duration.INFINITE)

  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      timeoutRunBlocking(coroutineTimeout, coroutineName = description.displayName) {
        base.evaluate()
      }
    }
  }
}