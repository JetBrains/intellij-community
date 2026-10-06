// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText

/**
 * @author Vladislav.Soroka
 */
class GradleSyncOutputParser : BuildOutputLineParserBridge() {

  companion object {
    private const val ERROR_PREFIX = "[sync error]"
    private const val WARNING_PREFIX = "[sync warning]"
  }

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    var kind = MessageEvent.Kind.ERROR
    var prefix = ERROR_PREFIX
    var prefixIndex = line.text.indexOf(ERROR_PREFIX)
    if (prefixIndex < 0) {
      kind = MessageEvent.Kind.WARNING
      prefix = WARNING_PREFIX
      prefixIndex = line.text.indexOf(WARNING_PREFIX)
      if (prefixIndex < 0) {
        return false
      }
    }

    val text = line.text.substring(prefixIndex + prefix.length).trim()
    multicaster.notifyBuildEvent(
      MessageEvent.builder(text, kind)
        .withParentId(taskId)
        .withDescription(BuildOutputText.createOutputText(line.ids, text), isSingleFlow)
        .build()
    )
    return true
  }

  private fun MessageEventBuilder.withDescription(line: BuildOutputText, isSingleFlow: Boolean): MessageEventBuilder =
    when (isSingleFlow) {
      true -> withOutputIds(line.ids)
      else -> withDescription(line.text)
    }
}
