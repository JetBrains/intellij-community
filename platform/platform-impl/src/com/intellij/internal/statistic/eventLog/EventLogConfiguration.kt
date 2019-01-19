// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.common.hash.Hashing
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.prefs.Preferences

object EventLogConfiguration {
  private val LOG = Logger.getInstance(EventLogConfiguration::class.java)
  private const val SALT_PREFERENCE_KEY = "event_log_salt"

  val sessionId: String = UUID.randomUUID().toString().shortedUUID()

  val deviceId: String = DeviceIdManager.getOrGenerateId()
  val bucket: Int = deviceId.asBucket()

  val build: String = ApplicationInfo.getInstance().build.asBuildNumber()

  private val salt: String = getOrGenerateSalt()
  private val anonymizedCache = ContainerUtil.newHashMap<String, String>()

  fun anonymize(data: String): String {
    if (StringUtil.isEmptyOrSpaces(data)) {
      return data
    }

    if (anonymizedCache.containsKey(data)) {
      return anonymizedCache[data] ?: ""
    }

    val hasher = Hashing.sha256().newHasher()
    hasher.putString(salt, StandardCharsets.UTF_8)
    hasher.putString(data, StandardCharsets.UTF_8)
    val result = hasher.hash().toString()
    anonymizedCache[data] = result
    return result
  }

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

  private fun getOrGenerateSalt(): String {
    val companyName = ApplicationInfoImpl.getShadowInstance().shortCompanyName
    val name = if (StringUtil.isEmptyOrSpaces(companyName)) "jetbrains" else companyName.toLowerCase(Locale.US)
    val prefs = Preferences.userRoot().node(name)

    var salt = prefs.get(SALT_PREFERENCE_KEY, null)
    if (StringUtil.isEmptyOrSpaces(salt)) {
      salt = UUID.randomUUID().toString()
      prefs.put(SALT_PREFERENCE_KEY, salt)
      LOG.info("Generating salt for the device")
    }
    return salt
  }
}
