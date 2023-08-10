// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.mock

import com.intellij.openapi.diagnostic.DefaultLogger
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.assertEquals

@TestOnly
class EditorConfigMockLogger : DefaultLogger("") {
  private var lastMessage: String? = null

  private var debugCalls = 0
  private var infoCalls = 0
  private var warnCalls = 0
  private var errorCalls = 0

  fun assertCallNumbers(debugCalls: Int = 0, infoCalls: Int = 0, warnCalls: Int = 0, errorCalls: Int = 0) {
    assertEquals(debugCalls, this.debugCalls)
    assertEquals(infoCalls, this.infoCalls)
    assertEquals(warnCalls, this.warnCalls)
    assertEquals(errorCalls, this.errorCalls)
  }

  fun assertLastMessage(message: String?) {
    assertEquals(message, lastMessage)
  }

  override fun isDebugEnabled(): Boolean = true

  override fun debug(message: String?, t: Throwable?) {
    debugCalls += 1
    lastMessage = message
  }

  override fun info(message: String?, t: Throwable?) {
    infoCalls += 1
    lastMessage = message
  }

  override fun warn(message: String?, t: Throwable?) {
    warnCalls += 1
    lastMessage = message
  }

  override fun error(message: String?, t: Throwable?, vararg details: String?) {
    errorCalls += 1
    lastMessage = message
  }
}
