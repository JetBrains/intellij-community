// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.jna.JnaLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.sun.jna.Structure
import com.sun.jna.platform.unix.LibC
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
object SuperUserStatus {

  /**
   * Returns `null` if the super user status is not yet computed.
   * Use [whenKnown] for a callback when the status is known
   */
  @JvmStatic
  @get:Contract(pure = false)
  val isSuperUserOrNull: Boolean?
    get() {
      request.start()
      if (request.isCompleted) {
        @OptIn(ExperimentalCoroutinesApi::class)
        return request.getCompleted()
      } else {
        return null
      }
    }

  /**
   * Blocks until super user status is computed
   */
  @JvmStatic
  val isSuperUser: Boolean
    get() {
      request.start()
      return request.asCompletableFuture().get()
    }

  fun whenKnown(@RequiresBackgroundThread action: suspend () -> Unit): Job {
    @OptIn(DelicateCoroutinesApi::class)
    return GlobalScope.launch(Dispatchers.IO) {
      request.join()
      action()
    }
  }

  @OptIn(DelicateCoroutinesApi::class, LowLevelLocalMachineAccess::class)
  private val request: Deferred<Boolean> = GlobalScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    try {
      if (!JnaLoader.isLoaded()) {
        return@async false
      }
      when {
        OS.CURRENT == OS.Windows -> WindowsElevationStatus.isElevated()
        else -> UnixUserStatus.isSuperUser()
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
