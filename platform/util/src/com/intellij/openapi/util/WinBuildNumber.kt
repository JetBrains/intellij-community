// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WinBuildNumber")
package com.intellij.openapi.util

import com.intellij.jna.JnaLoader
import com.intellij.openapi.diagnostic.Logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

internal fun getWinBuildNumber(): Long? =
  lazy { if (JnaLoader.isLoaded()) getWinBuildNumberInternal() else null }.value

private fun getWinBuildNumberInternal(): Long? =
  try {
    // this key is undocumented but mentioned heavily all over the Internet
    Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuildNumber").toLong()
  }
  catch (e: Exception) {
    Logger.getInstance(SystemInfo::class.java).warn("Unrecognized win version", e)
    null
  }
