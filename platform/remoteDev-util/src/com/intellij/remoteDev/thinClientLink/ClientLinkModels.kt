package com.intellij.remoteDev.thinClientLink

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This class (and [ClientToGtwMessage]) are used for communication between Gateway and JBClient.
 *
 * They could have different versions, so it's important to keep track of versions used.
 * Unknown messages (from version differences) will be logged and ignored.
 * Serialization format must be kept intact for backwards compatibility too, so any potential naming changes have to keep that in mind.
 */
@Serializable
@Internal
sealed class GtwToClientMessage {
  /** @since 2021.3 */
  @Serializable
  data class UpdateLink(val newLink: String, val forceReconnect: Boolean) : GtwToClientMessage()

  /** @since 2021.3 */
  @Serializable
  object GatewayReconnecting : GtwToClientMessage()

  /**
   * Invoke backend restart
   * @since 2022.1
   */
  @Serializable
  object RestartReady : GtwToClientMessage()

  /** @since 2021.3 */
  @Serializable
  object GatewayClose : GtwToClientMessage()

  /** @since 2022.1 */
  @Serializable
  data class GatewayLogs(val gtwLogs: String) : GtwToClientMessage()

  /** @since 2022.3 */
  @Serializable
  object RequestWindowFocus: GtwToClientMessage()
}


/**
 * @see [GtwToClientMessage]
 */
@Serializable
@Internal
sealed class ClientToGtwMessage {
  /** @since 2021.3 */
  @Serializable
  object ClientClosing : ClientToGtwMessage()

  /**
   * Ask GTW to prepare restart flag and response with RestartReady
   * @since 2022.1
   */
  @Serializable
  object ClientRestart : ClientToGtwMessage()

  /** @since 2022.1 */
  @Serializable
  object ClientAsksLogs : ClientToGtwMessage()

  /** @since 2021.3 */
  @Serializable
  data class ProjectOpenFailed(val exitCode: Int) : ClientToGtwMessage()

  /** @since 2021.3 */
  @Serializable
  object Ping : ClientToGtwMessage()
}

