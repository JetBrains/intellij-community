// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.diagnostic.Logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinReg


private fun getWinBuildNumberInternal(): Long? {
  try { // This key is undocumented, but mentioned heavily over the Internet and used by lots of people
    // It was there since NT: https://web.archive.org/web/20220113230741/https://i.imgur.com/sGCtErh.png
    // Exists in XP, 10 and 11
    return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                                               "CurrentBuildNumber").toLong()
  }
  catch (e: Exception) {
    if (e is NumberFormatException || e is Win32Exception) {
      Logger.getInstance(SystemInfo::class.java).warn("Bad win version", e)
      return null
    }
    else {
      throw e
    }
  }
}

/**
 * Returns Windows build number.
 * Code extracted to the separate class because of [Win32Exception] dependency that should be loaded lazily, so can't be used in Java
 * because classes in "catch" clause are loaded along with class
 */
internal fun getWinBuildNumber(): Long? = lazy { getWinBuildNumberInternal() }.value