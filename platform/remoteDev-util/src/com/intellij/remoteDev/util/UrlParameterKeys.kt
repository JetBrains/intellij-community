package com.intellij.remoteDev.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class UrlParameterKeys {
  companion object {
    const val projectPath = "projectPath"
    const val idePath = "idePath"
    const val host = "host"
    const val user = "user"
    const val port = "port"
    const val type = "type"
    const val deploy = "deploy"
    const val sshId = "ssh"
    const val buildNumber = "buildNumber"
    const val productCode = "productCode"
  }
}