package com.intellij.remoteDev.util

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.Throws

@ApiStatus.Experimental
data class RunInstalledDetails(
  val sshId: String,
  val projectPath: String,
  val IDEPath: String,
) {
  companion object {
    @Throws(IllegalStateException::class)
    fun fromParams(params: Map<String, String>): RunInstalledDetails {
      validateLinkParams(params)
      return RunInstalledDetails(
        params[UrlParameterKeys.sshId]!!,
        params[UrlParameterKeys.projectPath]!!,
        params[UrlParameterKeys.idePath]!!,
      )
    }
  }
}

@Throws(IllegalStateException::class)
private fun validateLinkParams(params: Map<String, String>) {
  val requiredKeys = arrayOf(UrlParameterKeys.sshId, UrlParameterKeys.type,
                             UrlParameterKeys.idePath, UrlParameterKeys.projectPath, UrlParameterKeys.deploy)
  for (key in requiredKeys) {
    if (!params.containsKey(key)) error("Invalid ssh link parameters: doesn't contain ${key}")
  }
  if (params[UrlParameterKeys.type] != CONNECTION_TYPE_SSH) {
    error("Invalid ssh link parameters: unexpected type '${params[UrlParameterKeys.type]}'")
  }
  if (params[UrlParameterKeys.deploy].toBoolean()) {
    error("Invalid ssh link parameters: deploy should be false")
  }
}
