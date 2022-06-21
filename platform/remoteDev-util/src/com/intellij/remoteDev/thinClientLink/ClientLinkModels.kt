package com.intellij.remoteDev.thinClientLink

import kotlinx.serialization.Serializable

@Serializable
sealed class GtwToClientMessage {
  @Serializable
  data class UpdateLink(val newLink: String, val forceReconnect: Boolean) : GtwToClientMessage()

  @Serializable
  object GatewayReconnecting : GtwToClientMessage()

  // invoke backend restart
  @Serializable
  object RestartReady : GtwToClientMessage()

  @Serializable
  object GatewayClose : GtwToClientMessage()

  @Serializable
  data class GatewayLogs(val gtwLogs: String) : GtwToClientMessage()
}


@Serializable
sealed class ClientToGtwMessage {
  @Serializable
  object ClientClosing : ClientToGtwMessage()

  // ask GTW to prepare restart flag and response with RestartReady
  @Serializable
  object ClientRestart : ClientToGtwMessage()

  @Serializable
  object ClientAsksLogs : ClientToGtwMessage()

  @Serializable
  data class ProjectOpenFailed(val exitCode: Int) : ClientToGtwMessage()

  @Serializable
  object Ping : ClientToGtwMessage()
}

