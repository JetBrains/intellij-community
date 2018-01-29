/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal.statistic.eventLog

import com.google.gson.Gson

object LogEventSerializer {
  private val bucket = -1
  private val recorderVersion = 1

  private val gson = Gson()

  fun toString(event: LogEvent): String {
    return "${event.timestamp}\t${event.recorderId}\t${recorderVersion}\t" +
           "${event.userUid}\t${event.sessionUid}\t${event.actionType}\t${bucket}\t" +
           gson.toJson(event.data)
  }
}