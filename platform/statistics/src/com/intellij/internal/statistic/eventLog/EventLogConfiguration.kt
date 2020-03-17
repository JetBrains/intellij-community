// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.common.hash.Hashing
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.MathUtil
import java.security.SecureRandom
import java.util.*
import java.util.prefs.Preferences

object EventLogConfiguration {
  private val LOG = Logger.getInstance(EventLogConfiguration::class.java)
  private const val SALT_PREFERENCE_KEY = "feature_usage_event_log_salt"

  val sessionId: String = UUID.randomUUID().toString().shortedUUID()

  val deviceId: String = DeviceIdManager.getOrGenerateId()
  val bucket: Int = deviceId.asBucket()

  val build: String = ApplicationInfo.getInstance().build.asBuildNumber()

  private val salt: ByteArray = getOrGenerateSalt()
  private val anonymizedCache = HashMap<String, String>()

  fun anonymize(data: String): String {
    if (StringUtil.isEmptyOrSpaces(data)) {
      return data
    }

    if (anonymizedCache.containsKey(data)) {
      return anonymizedCache[data] ?: ""
    }

    val hasher = Hashing.sha256().newHasher()
    hasher.putBytes(salt)
    hasher.putString(data, Charsets.UTF_8)
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
    return MathUtil.nonNegativeAbs(this.hashCode()) % 256
  }

  private fun getOrGenerateSalt(): ByteArray {
    val companyName = ApplicationInfoImpl.getShadowInstance().shortCompanyName
    val name = if (StringUtil.isEmptyOrSpaces(companyName)) "jetbrains" else companyName.toLowerCase(Locale.US)
    val prefs = Preferences.userRoot().node(name)

    var salt = prefs.getByteArray(SALT_PREFERENCE_KEY, null)
    if (salt == null) {
      salt = ByteArray(32)
      SecureRandom().nextBytes(salt)
      prefs.putByteArray(SALT_PREFERENCE_KEY, salt)
      LOG.info("Generating salt for the device")
    }
    return salt
  }
}
