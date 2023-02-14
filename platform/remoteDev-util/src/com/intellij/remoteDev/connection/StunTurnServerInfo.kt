package com.intellij.remoteDev.connection

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class StunTurnServerInfo(
  val uri: String,
  val username: String?,
  val password: String?
)