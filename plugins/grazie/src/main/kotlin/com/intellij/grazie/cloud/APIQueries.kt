package com.intellij.grazie.cloud

import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.exceptions.HTTPStatusException
import ai.grazie.model.cloud.exceptions.HttpExceptionBase
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object APIQueries {
  @ApiStatus.Internal
  suspend fun <T> handleExceptions(project: Project, service: BackgroundCloudService? = null, compute: suspend () -> T?): T? =
    try {
      val result = compute()
      GrazieCloudNotifications.Connection.connectionStable(project, service)
      result
    }
    catch (e: HTTPStatusException.AccessProhibited) {
      thisLogger().info("Authorisation error in Grazie functionality", e)
      null
    }
    catch (e: HttpExceptionBase) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: HTTPConnectionError) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: HttpRequestTimeoutException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: IOException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error(RuntimeException(e))
      null
    }
}