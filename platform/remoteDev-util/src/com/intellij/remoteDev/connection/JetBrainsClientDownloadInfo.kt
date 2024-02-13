package com.intellij.remoteDev.connection

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class JetBrainsClientDownloadInfo(
  val hostBuildNumber: String,
  val compatibleClientUrl: String,
  val compatibleJreUrl: String?,
  val downloadPgpPublicKeyUrl: String?
)