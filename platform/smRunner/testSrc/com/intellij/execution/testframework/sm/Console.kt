// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm

import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import java.text.ParseException

/**
 * Class mocks up test console.
 * When you send several chunks of text with the same type console displays them one after another, so they are indistinguishable
 * for user.
 *
 * However, messages of different color and TC messages are processed separately.
 * This merges texts of same type, but not TC nor texts with different colors.
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
  try {
    return if (ServiceMessage.parse(text.trim()) != null) text else null
  }
  catch (_: ParseException) {
    return null
  }
}

private sealed class ConsoleRecord(val text: String) {
  class TCMessage(text: String) : ConsoleRecord(text)
  class TextMessage(text: String, val type: Key<*>) : ConsoleRecord(text)
}
