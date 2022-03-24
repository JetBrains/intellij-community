// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * @see UncaughtExceptionsRule
 */
class UncaughtExceptionsExtension : BeforeEachCallback, AfterEachCallback {

  private var defaultHandler: Thread.UncaughtExceptionHandler? = null
  private val myExceptions = ContainerUtil.createConcurrentList<Throwable>()

  override fun beforeEach(context: ExtensionContext?) {
    defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    myExceptions.clear()
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      myExceptions.add(e)
    }
  }

  override fun afterEach(context: ExtensionContext?) {
    Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
    val e = when (myExceptions.size) {
      0 -> return
      1 -> myExceptions[0]
      else -> CompoundRuntimeException(myExceptions)
    }
    throw AssertionError("Uncaught exceptions", e)
  }
}
