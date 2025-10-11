package com.intellij.grazie.cloud

import ai.grazie.model.cloud.GrazieHeaders
import ai.grazie.model.cloud.exceptions.HttpExceptionBase
import com.intellij.grazie.cloud.GrazieCloudConnectionState.ConnectionState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

object GrazieCloudNotifications {

  object Connection {

    @Synchronized
    @JvmStatic
    fun connectionError(project: Project, service: BackgroundCloudService?, e: Throwable) {
      var msg = "Cloud connection error"
      if (e is HttpExceptionBase) {
        val traceId = e.headers.get(GrazieHeaders.TRACE_ID)?.singleOrNull()
        if (traceId != null) msg += "; traceId=$traceId"
      }
      // wrap in `RuntimeException` to retain the caller stack trace because `e` might come from a different thread
      thisLogger().info(msg, RuntimeException(e))
      if (service == null) return

      GrazieCloudConnectionState.stateChanged(ConnectionState.Error, service)
    }

    @Synchronized
    @JvmStatic
    fun connectionStable(project: Project, service: BackgroundCloudService?) {
      if (service == null ||
          GrazieCloudConnectionState.stateChanged(ConnectionState.Stable, service) == ConnectionState.Error) {
        return
      }
    }
  }
}
