package com.intellij.remoteDev.util

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.Throws

const val CONNECTION_TYPE_SSH = "ssh"

@ApiStatus.Experimental
data class SshLinkConnectionDetails(
  val IDEPath: String,
  val projectPath: String,
  val host: String,
  val port: Int,
  val user: String,
) {
  companion object {
    @Throws(IllegalStateException::class)
    fun fromParams(params: Map<String, String>): SshLinkConnectionDetails {
      validateSshLinkParams(params)
      return SshLinkConnectionDetails(
        params[UrlParameterKeys.idePath]!!,
        params[UrlParameterKeys.projectPath]!!,
        params[UrlParameterKeys.host]!!,
        params[UrlParameterKeys.port]!!.toInt(),
        params[UrlParameterKeys.user]!!,
      )
    }
  }

  fun toParams(): Map<String, String> {
    return mapOf(
      UrlParameterKeys.idePath to IDEPath,
      UrlParameterKeys.projectPath to projectPath,
      UrlParameterKeys.host to host,
      UrlParameterKeys.port to port.toString(),
      UrlParameterKeys.user to user,
      UrlParameterKeys.type to CONNECTION_TYPE_SSH,
      UrlParameterKeys.deploy to false.toString()
    )
  }
}

@Throws(IllegalStateException::class)
private fun validateSshLinkParams(params: Map<String, String>) {
  val requiredKeys = arrayOf(UrlParameterKeys.host, UrlParameterKeys.user, UrlParameterKeys.port,
    UrlParameterKeys.type, UrlParameterKeys.idePath, UrlParameterKeys.projectPath, UrlParameterKeys.deploy)
  for (key in requiredKeys) {
    if (!params.containsKey(key)) error("Invalid ssh link parameters: doesn't contain ${key}")
  }
  if (params[UrlParameterKeys.port]?.toInt() == null) {
    error("Invalid ssh link parameters: failed to parse port. Port param: '${params[UrlParameterKeys.port]}'")
  }
  if (params[UrlParameterKeys.type] != CONNECTION_TYPE_SSH) {
    error("Invalid ssh link parameters: unexpected type '${params[UrlParameterKeys.type]}'")
  }
  if (params[UrlParameterKeys.deploy].toBoolean()) {
    error("Invalid ssh link parameters: deploy should be false")
  }
}
