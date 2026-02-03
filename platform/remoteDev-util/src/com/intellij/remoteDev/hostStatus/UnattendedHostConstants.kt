package com.intellij.remoteDev.hostStatus

import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class UnattendedHostPerProjectStatus(
  val projectName: String,
  val projectPath: String,
  val projectPathLink: String?,
  val dateLastOpened: Long? = null,

  val joinLink: String,
  val httpLink: String?,
  val gatewayLink: String?,

  val controllerConnected: Boolean,
  val secondsSinceLastControllerActivity: Long,
  val backgroundTasksRunning: Boolean,

  val users: List<String>,
)

@ApiStatus.Experimental
data class UnattendedHostStatus(
  val appPid: Long,
  val appVersion: String,
  val runtimeVersion: String,
  val unattendedMode: Boolean,
  val backendUnresponsive: Boolean,
  val modalDialogIsOpened: Boolean,
  val idePath: String,

  // available since 2024.1 - helps determine which host IDE is which after restart
  val ideIdentityString: String?,

  // join links at app level are available since 2023.1
  val joinLink: String?,
  val httpLink: String?,
  val gatewayLink: String?,

  val projects: List<UnattendedHostPerProjectStatus>? = null
) {

  fun toJson(): String {
    return gson.toJson(this)
  }

  /**
   * Shorter implementation not to pollute the logs on every query
   */
  @ApiStatus.Internal
  fun toShortJson(): String {
    val shortStatus = UnattendedHostShortStatus(this)
    return minifiedGson.toJson(shortStatus)
  }

  fun productCode(): String? {
    val productAndVersion = appVersion.split("-")
    return if (productAndVersion.size != 2) {
      null
    } else {
      productAndVersion[0]
    }
  }

  companion object {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val minifiedGson = GsonBuilder().disableHtmlEscaping().create()

    fun fromJson(json: String): UnattendedHostStatus {
      return gson.fromJson(json, UnattendedHostStatus::class.java)
    }
  }
}

@ApiStatus.Experimental
object UnattendedHostConstants {
  val LOG = logger<UnattendedHostConstants>()
  const val STATUS_PREFIX = "STATUS:\n"
}


private data class UnattendedHostShortStatus(
  val backendUnresponsive: Boolean,
  val modalDialogIsOpened: Boolean,

  val joinLink: String?,

  val projects: List<UnattendedHostPerProjectShortStatus>? = null,
) {
  constructor(status: UnattendedHostStatus) : this(
    backendUnresponsive = status.backendUnresponsive,
    modalDialogIsOpened = status.modalDialogIsOpened,
    joinLink = status.joinLink,
    projects = status.projects?.map { UnattendedHostPerProjectShortStatus(it) }
  )
}

private data class UnattendedHostPerProjectShortStatus(
  val projectName: String,
  val projectPath: String,
  val dateLastOpened: Long? = null,

  val controllerConnected: Boolean,
  val secondsSinceLastControllerActivity: Long,
  val backgroundTasksRunning: Boolean,

  val users: List<String>,
) {
  constructor(status: UnattendedHostPerProjectStatus) : this(
    projectName = status.projectName,
    projectPath = status.projectPath,
    dateLastOpened = status.dateLastOpened,
    controllerConnected = status.controllerConnected,
    secondsSinceLastControllerActivity = status.secondsSinceLastControllerActivity,
    backgroundTasksRunning = status.backgroundTasksRunning,
    users = status.users
  )
}
