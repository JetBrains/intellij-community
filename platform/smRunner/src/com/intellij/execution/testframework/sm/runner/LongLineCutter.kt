// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.execution.testframework.sm.ServiceMessageUtil
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage

private const val ELLIPSIS = "<...>"

private val FIELDS_NOT_TO_TOUCH = setOf("name", "duration", "type", "flowId", "nodeId", "parentNodeId")
private val EXPECTED_ACTUAL = arrayOf("expected", "actual")


/**
 * If [text] is longer than [maxLength] cut it and insert [ELLIPSIS] in cut.
 * If it is a [ServiceMessage], then find longest attribute and cut it leaving [margin] as prefix and postfix
 * [EXPECTED_ACTUAL] attrs both are cut.
 *
 */
fun cutLineIfTooLong(text: String, maxLength: Int = ConsoleBuffer.getCycleBufferSize(), margin: Int = 1000): String {
  val minValueLengthToCut = (margin * 2) + ELLIPSIS.length
  if (text.length <= maxLength || maxLength < minValueLengthToCut) {
    return text
  }

  val message = ServiceMessageUtil.parse(text.trim(), false, false)

  if (message == null) {
    //Not a message, cut as regular text
    return text.substring(0, maxLength - ELLIPSIS.length) + ELLIPSIS
  }

  val attributes = HashMap(message.attributes)
  val attributesToCut = attributes
    .filter { it.key !in FIELDS_NOT_TO_TOUCH }
    .toList()
    .sortedByDescending { it.second.length }
    .map { it.first }

  val shortener = Shortener(attributes, text.length, margin, minValueLengthToCut)
  for (attr in attributesToCut) {
    if (shortener.currentLength < maxLength) {
      break
    }
    shortener.shortenAttribute(attr)
    if (attr in EXPECTED_ACTUAL) {
      EXPECTED_ACTUAL.forEach(shortener::shortenAttribute)
    }
  }
  return ServiceMessage.asString(message.messageName, attributes)
}

private class Shortener(private val attributes: MutableMap<String, String>,
                        var currentLength: Int,
                        private val margin: Int,
                        private val minValueLengthToCut: Int) {
  private val shortened = mutableSetOf<String>()
  fun shortenAttribute(attribute: String) {
    if (attribute in shortened) {
      return
    }
    val value = attributes[attribute] ?: return
    if (value.length <= minValueLengthToCut) { // Too short to cut
      return
    }

    val lenBefore = value.length
    val newValue = StringBuilder(value).replace(margin, value.length - margin, ELLIPSIS).toString()
    currentLength -= (lenBefore - newValue.length)
    attributes[attribute] = newValue
    shortened.add(attribute)
  }
}
