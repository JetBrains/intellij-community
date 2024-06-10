// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon

import java.io.File
import java.io.Serializable
import java.text.DateFormat
import java.util.*

/**
 * @author Vladislav.Soroka
 */
class DaemonState(val pid: Long?,
                  val token: ByteArray?,
                  val version: String?,
                  val status: String,
                  val reason: String?,
                  val timestamp: Long,
                  val daemonExpirationStatus: String?,
                  val daemonOpts: Collection<String>?,
                  val javaHome: File?,
                  val idleTimeout: Int?,
                  val registryDir: File?) : Serializable {

  val description: String by lazy { calculateDescription() }

  private fun calculateDescription(): String {
    val info = StringBuilder()
    info
      .append(pid).append(" ")
      .append(timestamp.asFormattedTimestamp()).append(" ")
      .append(status).append(" ")
    if (!version.isNullOrEmpty()) {
      info.append("Gradle version: ").append(version)
    }
    if (!daemonExpirationStatus.isNullOrEmpty()) {
      info.append("\nExpiration status: ").append(daemonExpirationStatus)
    }
    if (!reason.isNullOrEmpty()) {
      info.append("\nStop reason: ").append(reason)
    }
    if (registryDir != null) {
      info.append("\nDaemons dir: ").append(registryDir)
    }
    if (javaHome != null) {
      info.append("\nJava home: ").append(javaHome.path)
    }
    if (!daemonOpts.isNullOrEmpty()) {
      info.append("\nDaemon options: ").append(daemonOpts)
    }
    if (idleTimeout != null) {
      info.append("\nIdle timeout: ").append(idleTimeout)
    }
    return info.toString()
  }

  private fun Long.asFormattedTimestamp() = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
}
