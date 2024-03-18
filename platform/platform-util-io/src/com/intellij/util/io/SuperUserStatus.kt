// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.jna.JnaLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Structure
import com.sun.jna.platform.unix.LibC
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference

object SuperUserStatus {
  @JvmStatic
  val isSuperUser: Boolean by lazy {
    try {
      when {
        !JnaLoader.isLoaded() -> false
        SystemInfo.isWindows -> WindowsElevationStatus.isElevated()
        SystemInfo.isUnix -> UnixUserStatus.isSuperUser()
        else -> false
      }
    }
    catch (t: Throwable) {
      logger<SuperUserStatus>().warn(t)
      false
    }
  }
}

//<editor-fold desc="Windows implementation">
@Suppress("ClassName", "PropertyName")
private object WindowsElevationStatus {
  fun isElevated(): Boolean {
    val tokenHandle = WinNT.HANDLEByReference()

    val currentProcess = Kernel32.INSTANCE.GetCurrentProcess()
    if (!Advapi32.INSTANCE.OpenProcessToken(currentProcess, WinNT.TOKEN_ADJUST_PRIVILEGES or WinNT.TOKEN_QUERY, tokenHandle)) {
      val lastError = Kernel32.INSTANCE.GetLastError()
      throw RuntimeException("OpenProcessToken: ${lastError} ${Kernel32Util.formatMessageFromLastErrorCode(lastError)}")
    }

    try {
      val cbNeeded = IntByReference(0)
      val token = TOKEN_ELEVATION()
      val infoClass = WinNT.TOKEN_INFORMATION_CLASS.TokenElevation
      if (!Advapi32.INSTANCE.GetTokenInformation(tokenHandle.value, infoClass, token, token.size(), cbNeeded)) {
        val lastError = Kernel32.INSTANCE.GetLastError()
        throw RuntimeException("GetTokenInformation: ${lastError} ${Kernel32Util.formatMessageFromLastErrorCode(lastError)}")
      }

      return token.TokenIsElevated.toInt() != 0
    }
    finally {
      Kernel32.INSTANCE.CloseHandle(tokenHandle.value)
    }
  }

  @Structure.FieldOrder("TokenIsElevated")
  class TOKEN_ELEVATION : Structure() {
    @JvmField var TokenIsElevated = WinDef.DWORD(0)
  }
}
//</editor-fold>

//<editor-fold desc="Unix implementation">
private object UnixUserStatus {
  fun isSuperUser(): Boolean = LibC.INSTANCE.geteuid() == 0
}
//</editor-fold>
