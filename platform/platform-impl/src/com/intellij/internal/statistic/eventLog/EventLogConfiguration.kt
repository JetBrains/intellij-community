// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import java.util.*

object EventLogConfiguration {
  val sessionId: String = UUID.randomUUID().toString().shortedUUID()

  val deviceId: String = DeviceIdManager.getOrGenerateId()
  val bucket: Int = deviceId.asBucket()

  val build: String = ApplicationInfo.getInstance().build.asBuildNumber()

  private fun String.shortedUUID(): String {
    val start = this.lastIndexOf('-')
    if (start > 0 && start + 1 < this.length) {
      return this.substring(start + 1)
    }
    return this
  }

  private fun BuildNumber.asBuildNumber(): String {
    val str = this.asStringWithoutProductCodeAndSnapshot()
    return if (str.endsWith(".")) str + "0" else str
  }

  private fun String.asBucket(): Int {
    return Math.abs(this.hashCode()) % 256
  }
}
