package com.intellij.remoteDev.util

import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ClientVersionUtil {
  fun isJBCSeparateConfigSupported(clientVersion: String): Boolean {
    val clientBuild = BuildNumber.fromString(clientVersion)
    val supportedSinceBuild = BuildNumber("", 233, 173)
    return clientBuild != null && clientBuild >= supportedSinceBuild
  }
}