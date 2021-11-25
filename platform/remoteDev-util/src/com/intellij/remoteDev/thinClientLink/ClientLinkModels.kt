package com.intellij.remoteDev.thinClientLink

import kotlinx.serialization.Serializable

@Serializable
sealed class GtwToClientMessage {
  @Serializable
  data class UpdateLink(val newLink: String, val forceReconnect: Boolean): GtwToClientMessage()

  @Serializable
  object GatewayReconnecting : GtwToClientMessage()

  @Serializable
  object GatewayClose : GtwToClientMessage()
}


@Serializable
sealed class ClientToGtwMessage {
  @Serializable
  object ClientClosing : ClientToGtwMessage()

  @Serializable
  data class ProjectOpenFailed(val exitCode: Int) : ClientToGtwMessage()

  @Serializable
  object Ping: ClientToGtwMessage()
}

