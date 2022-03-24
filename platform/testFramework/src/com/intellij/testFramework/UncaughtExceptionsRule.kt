// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * @see UncaughtExceptionsExtension
 */
class UncaughtExceptionsRule : TestRule {

  private val myExceptions = ContainerUtil.createConcurrentList<Throwable>()

  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      val handler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { _, e ->
        myExceptions.add(e)
      }
      try {
        base.evaluate()
      }
      finally {
        Thread.setDefaultUncaughtExceptionHandler(handler)
      }
      val e = when (myExceptions.size) {
        0 -> return
        1 -> myExceptions[0]
        else -> CompoundRuntimeException(myExceptions)
      }
      throw AssertionError("Uncaught exceptions", e)
    }
  }
}
