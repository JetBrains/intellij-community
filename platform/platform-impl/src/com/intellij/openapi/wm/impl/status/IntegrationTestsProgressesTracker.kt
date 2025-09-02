// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.util.text.StringUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

internal object IntegrationTestsProgressesTracker {
  private val enabled = ApplicationManagerEx.isInIntegrationTest() && System.getProperty("idea.performanceReport.projectName") != null
  private val lock = Object()
  private val timestamps: MutableMap<ProgressModel, Long> = ConcurrentHashMap<ProgressModel, Long>()

  // See com.intellij.openapi.wm.impl.status.ProgressComponent.updateProgressNow
  private fun getTitle(indicatorModel: ProgressModel): String {
    val text = indicatorModel.getText()
    return if (!StringUtil.isEmpty(text)) text!! else indicatorModel.title
  }

  fun progressStarted(indicatorModel: ProgressModel) {
    if (!enabled) return
    synchronized(lock) {
      timestamps[indicatorModel] = System.currentTimeMillis()
    }
  }

  fun progressTitleChanged(indicatorModel: ProgressModel, oldTextValue: String, newTextValue: String) {
    if (!enabled) return
    if (oldTextValue == newTextValue) return
    synchronized(lock) {
      val now = System.currentTimeMillis()
      val was = timestamps.replace(indicatorModel, now)
      if (isUnnoticeableUnnamedProgress(oldTextValue, was, now)) return
      val message = createMessage(was, now, oldTextValue, indicatorModel.visibleInStatusBar, "changed message")
      sendMessage(message)
    }
  }

  private fun createMessage(was: Long?, now: Long, text: String, visibleInStatusBar: Boolean, action: String): String {
    val longer = when {
      was == null -> "unknown"
      was + 3000 < now -> "true"
      else -> "false"
    }
    val timeInMillis = if (was == null) "?" else (now - was).toString()
    return "Progress Indicator Test Stats:\n" +
           "v2\n" +
           (if (visibleInStatusBar) "Primary" else "Secondary") + "\n" +
           "$action\n" +
           "longer than 3 seconds: $longer\n" +
           "time: $timeInMillis ms\n" +
           "message: " + text.ifBlank { "<empty>" }
  }

  private fun isUnnoticeableUnnamedProgress(text: String, was: Long?, now: Long): Boolean {
    return text.isBlank() && was != null && (now - was < 200)
  }

  fun progressStopped(indicatorModel: ProgressModel?) {
    if (!enabled) return
    if (indicatorModel == null) return
    synchronized(lock) {
      val now = System.currentTimeMillis()
      val was = timestamps.remove(indicatorModel)
      val title = getTitle(indicatorModel)
      if (isUnnoticeableUnnamedProgress(title, was, now)) return
      sendMessage(createMessage(was, now, title, indicatorModel.visibleInStatusBar, "stopped"))
    }
  }

  private fun sendMessage(message: String): Future<*> = ApplicationManager.getApplication().executeOnPooledThread {
    throw RuntimeException(message)
  }

}