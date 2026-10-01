// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.util.function.Consumer

/**
 * @author Vladislav.Soroka
 */
class GradleSyncOutputParser : BuildOutputParser {

  companion object {
    private const val ERROR_PREFIX = "[sync error]"
    private const val WARNING_PREFIX = "[sync warning]"
  }

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    var kind = MessageEvent.Kind.ERROR
    var prefix = ERROR_PREFIX
    var prefixIndex = line.indexOf(ERROR_PREFIX)
    if (prefixIndex < 0) {
      kind = MessageEvent.Kind.WARNING
      prefix = WARNING_PREFIX
      prefixIndex = line.indexOf(WARNING_PREFIX)
      if (prefixIndex < 0) {
        return false
      }
    }

    val text = line.substring(prefixIndex + prefix.length).trim()
    messageConsumer.accept(MessageEventImpl(reader.getParentEventId(), kind, null, text, text))
    return true
  }
}
