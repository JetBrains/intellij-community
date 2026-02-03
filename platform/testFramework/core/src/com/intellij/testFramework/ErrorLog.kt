// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import kotlinx.collections.immutable.persistentListOf
import java.util.concurrent.atomic.AtomicReference

internal class ErrorLog {

  private val errors = AtomicReference(persistentListOf<LoggedError>())

  fun recordLoggedError(message: String?, t: Throwable?) {
    val logged = LoggedError(message, t)
    errors.updateAndGet {
      it.add(logged)
    }
  }

  /**
   * Takes all collected errors and clears the state: the next invocation will return an empty list, unless another error is logged.
   */
  fun takeLoggedErrors(): List<LoggedError> {
    return errors.getAndSet(persistentListOf())
  }
}
