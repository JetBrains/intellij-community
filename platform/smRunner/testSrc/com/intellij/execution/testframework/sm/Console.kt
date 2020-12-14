// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm

import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import java.text.ParseException

/**
 * Class mocks up test console.
 * It collects messages and stores them separately from stdout text.
 * It then returns messages with [toList] to simplify testing
 */
internal class Console {
  private val output = arrayListOf<ConsoleRecord>()
  fun processText(text: String, type: Key<*>) {
    val tcMessage = asTcMessage(text)
    if (tcMessage != null) {
      output.add(ConsoleRecord.TCMessage(tcMessage))
      return
    }
   output.add(ConsoleRecord.TextMessage(text, type))
  }

  fun toList() = output.map(ConsoleRecord::text)

  fun toArray() = toList().toTypedArray()
}


private fun asTcMessage(text: String): String? {
  return try {
    if (ServiceMessageUtil.parse(text.trim(), false) != null) text else null
  }
  catch (_: ParseException) {
    null
  }
}

private sealed class ConsoleRecord(val text: String) {
  class TCMessage(text: String) : ConsoleRecord(text)
  class TextMessage(text: String, val type: Key<*>) : ConsoleRecord(text)
}
