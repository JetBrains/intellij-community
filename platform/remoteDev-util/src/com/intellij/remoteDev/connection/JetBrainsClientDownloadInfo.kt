package com.intellij.remoteDev.connection

import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class JetBrainsClientDownloadInfo(
  val hostBuildNumber: BuildNumber,
  val clientBuildNumber: BuildNumber = hostBuildNumber,
  val compatibleClientUrl: String,
  val compatibleJreUrl: String?,
  val downloadPgpPublicKeyUrl: String?
)