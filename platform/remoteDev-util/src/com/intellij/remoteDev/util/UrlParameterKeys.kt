package com.intellij.remoteDev.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class UrlParameterKeys {
  companion object {
    const val projectPath = "projectPath"
    const val downloadLocation = "downloadLocation"
    const val idePath = "idePath"
    const val host = "host"
    const val user = "user"
    const val port = "port"
    const val type = "type"
    const val deploy = "deploy"
    const val sshId = "ssh"
    const val buildNumber = "buildNumber"
    const val productCode = "productCode"
    const val remoteId = "remoteId"
    @Deprecated("Use sourceUrl")
    const val download = "download"
    const val sourceUrl = "sourceUrl"
    // should only be allowed for locally round-tripped URLs, not from external sources
    const val localUploadPath = "localUploadPath"
    // used in Gateway-from-IDE scenario to go around the local path limitation
    const val runFromIdeToken = "runFromIdeToken"
    const val runFromIdeTokenEnvVar = "GTW_FROM_IDE_TOKEN"
    const val cloudWorkstationId = "cloudWorkstationId"
    const val gitpodHost = "gitpodHost"
    const val awsEnvId = "aws.codecatalyst.env.id"
  }
}