// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

/**
 * Connects different IDE agents during test session
 */
@ApiStatus.Internal
interface LambdaTestBridge {
  /**
   * This method sends calls into every connected protocol to ensure all events which
   *  this process sent into protocol were successfully received on the other side
   * Use this method after a test to preserve correct order of messages
   *  in protocol `IDE` <-> `IDE` because test framework works via
   *  different protocol `IDE` <-> `Test Process`
   */
  suspend fun syncProtocolEvents()
}