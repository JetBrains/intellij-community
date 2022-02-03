package com.intellij.remoteDev.connection

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CodeWithMeSessionInfoProvider {
  val hostBuildNumber: String

  val compatibleClientUrl: String
  val compatibleClientName: String

  val compatibleJreUrl: String
  val compatibleJreName: String
  val isUnattendedMode: Boolean

  // todo: do i need these fields?
  // https://www.jetbrains.com/help/cwm/code-with-me-administration-guide.html#manual_config
  val hostFeaturesToEnable: Set<String>?

  val stunTurnServers: List<StunTurnServerInfo>?

  val downloadPgpPublicKeyUrl: String?
}