// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.performance

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * @author yole
 */

class LatencyRecorderImpl : LatencyRecorder {
  override fun recordLatencyAwareAction(editor: Editor, actionId: String, timestampMs: Long) {
    (editor as? EditorImpl)?.recordLatencyAwareAction(actionId, timestampMs)
  }
}

class LatencyRecord {
  var totalKeysTyped: Int = 0
  var totalLatency: Long = 0L
  var maxLatency: Long = 0L

  fun update(latencyInMS: Long) {
    totalKeysTyped++
    totalLatency += latencyInMS
    if (latencyInMS > maxLatency) {
      maxLatency = latencyInMS
    }
  }

  val averageLatency: Long get() = totalLatency / totalKeysTyped
}

data class LatencyDistributionRecordKey(val name: String)

class LatencyDistributionRecord(val key: LatencyDistributionRecordKey) {
  val totalLatency: LatencyRecord = LatencyRecord()
  val actionLatencyRecords: MutableMap<String, LatencyRecord> = mutableMapOf<String, LatencyRecord>()

  fun update(action: String, latencyInMS: Long) {
    totalLatency.update(latencyInMS)
    actionLatencyRecords.getOrPut(action) { LatencyRecord() }.update(latencyInMS)
  }
}

val latencyMap: MutableMap<LatencyDistributionRecordKey, LatencyDistributionRecord> = mutableMapOf()

var currentLatencyRecordKey: LatencyDistributionRecordKey? = null

fun recordTypingLatency(editor: Editor, action: String, latencyInMS: Long) {
  val key = currentLatencyRecordKey ?: run {
    val fileType = FileDocumentManager.getInstance().getFile(editor.document)?.fileType ?: return
    LatencyDistributionRecordKey(fileType.name)
  }
  val latencyRecord = latencyMap.getOrPut(key) {
    LatencyDistributionRecord(key)
  }
  latencyRecord.update(getActionKey(action), latencyInMS)
}

fun getActionKey(action: String): String =
  if (action.length == 1) {
    when(action[0]) {
      in 'A'..'Z', in 'a'..'z', in '0'..'9' -> "Letter"
      ' ' -> "Space"
      '\n' -> "Enter"
      else -> action
    }
  }
  else action

