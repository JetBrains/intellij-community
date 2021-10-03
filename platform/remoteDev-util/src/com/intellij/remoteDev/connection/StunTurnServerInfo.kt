package com.intellij.remoteDev.connection

data class StunTurnServerInfo(
  val uri: String,
  val username: String?,
  val password: String?
)