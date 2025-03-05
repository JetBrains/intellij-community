// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.openapi.util.io.FileUtil
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DaemonState

    if (pid != other.pid) return false
    if (idleTimeout != other.idleTimeout) return false
    if (version != other.version) return false
    if (daemonOpts != other.daemonOpts) return false
    if (!FileUtil.filesEqual(javaHome, other.javaHome)) return false
    if (!FileUtil.filesEqual(registryDir, other.registryDir)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = pid?.hashCode() ?: 0
    result = 31 * result + (idleTimeout ?: 0)
    result = 31 * result + (version?.hashCode() ?: 0)
    result = 31 * result + (daemonOpts?.hashCode() ?: 0)
    result = 31 * result + FileUtil.fileHashCode(javaHome)
    result = 31 * result + FileUtil.fileHashCode(registryDir)
    return result
  }
}
