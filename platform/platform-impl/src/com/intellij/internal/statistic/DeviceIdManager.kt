// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import java.text.SimpleDateFormat
import java.util.*
import java.util.prefs.Preferences

object DeviceIdManager {
  private val LOG = Logger.getInstance(DeviceIdManager::class.java)

  private const val DEVICE_ID_SHARED_FILE = "PermanentDeviceId"
  private const val DEVICE_ID_PREFERENCE_KEY = "device_id"

  fun getOrGenerateId(): String {
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val prefs = getPreferences(appInfo)

    var deviceId = prefs.get(DEVICE_ID_PREFERENCE_KEY, null)
    if (StringUtil.isEmptyOrSpaces(deviceId)) {
      deviceId = generateId(Calendar.getInstance(), getOSChar())
      prefs.put(DEVICE_ID_PREFERENCE_KEY, deviceId)
      LOG.info("Generating new Device ID")
    }

    if (appInfo.isVendorJetBrains && SystemInfo.isWindows) {
      deviceId = PermanentInstallationID.syncWithSharedFile(DEVICE_ID_SHARED_FILE, deviceId, prefs, DEVICE_ID_PREFERENCE_KEY)
    }

    return deviceId
  }

  private fun getPreferences(appInfo: ApplicationInfoEx): Preferences {
    val companyName = appInfo.shortCompanyName
    val name = if (StringUtil.isEmptyOrSpaces(companyName)) "jetbrains" else companyName.toLowerCase(Locale.US)
    return Preferences.userRoot().node(name)
  }

  /**
   * Device id is generating by concatenating following values:
   * Current date, written in format ddMMyy, where year coerced between 2000 and 2099
   * Character, representing user's OS (see [getOSChar])
   * [toString] call on representation of [UUID.randomUUID]
   */
  fun generateId(calendar: Calendar, OSChar: Char): String {
    calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR).coerceIn(2000, 2099))
    return SimpleDateFormat("ddMMyy").format(calendar.time) + OSChar + UUID.randomUUID().toString()
  }

  private fun getOSChar() = if (SystemInfo.isWindows) '1' else if (SystemInfo.isMac) '2' else if (SystemInfo.isLinux) '3' else '0'
}