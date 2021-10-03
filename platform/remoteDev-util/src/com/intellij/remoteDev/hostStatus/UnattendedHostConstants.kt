package com.intellij.remoteDev.hostStatus

import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger

data class UnattendedHostPerProjectStatus(
  val projectName: String,
  val projectPath: String,
  var dateLastOpened: Long? = null,

  val joinLink: String,
  val permaLink: String? = null,
  val httpLink: String?,
  val gatewayLink: String?,

  val controllerConnected: Boolean,
  val secondsSinceLastControllerActivity: Long,
  val backgroundTasksRunning: Boolean,

  val users: List<String>,
)

data class UnattendedHostStatus(
  val appPid: Long,
  val appVersion: String,
  val runtimeVersion: String,
  val unattendedMode: Boolean,
  val idePath: String,
  val projects: List<UnattendedHostPerProjectStatus>? = null
) {

  fun toJson(): String {
    return gson.toJson(this)
  }

  companion object {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun fromJson(json: String): UnattendedHostStatus {
      return gson.fromJson(json, UnattendedHostStatus::class.java)
    }
  }
}

@Suppress("MemberVisibilityCanBePrivate")
object UnattendedHostConstants {
  val LOG = logger<UnattendedHostConstants>()
  const val STATUS_PREFIX = "STATUS:\n"
}