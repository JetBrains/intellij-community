package com.intellij.remoteServer.ir

import com.intellij.execution.Platform
import com.intellij.openapi.util.SystemInfo

data class RemotePlatform(val os: String?, val osVersion: String?, val arch: String?) {

  val platform: Platform = if (os?.startsWith("windows") == true) Platform.UNIX else Platform.WINDOWS

  companion object {
    @JvmField
    val CURRENT: RemotePlatform = RemotePlatform(SystemInfo.OS_NAME, SystemInfo.OS_VERSION, SystemInfo.OS_ARCH)
  }

}


