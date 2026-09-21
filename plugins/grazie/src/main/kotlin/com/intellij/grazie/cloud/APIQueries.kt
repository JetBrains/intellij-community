package com.intellij.grazie.cloud

import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.exceptions.HTTPStatusException
import ai.grazie.model.cloud.exceptions.HttpExceptionBase
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object APIQueries {
  @ApiStatus.Internal
  suspend fun <T> handleExceptions(compute: suspend () -> T?): T? =
    try {
      compute()
    }
    catch (e: HTTPStatusException.AccessProhibited) {
      thisLogger().info("Authorisation error in Grazie functionality", e)
      null
    }
    catch (_: HttpExceptionBase) {
      null
    }
    catch (_: HTTPConnectionError) {
      null
    }
    catch (_: HttpRequestTimeoutException) {
      null
    }
    catch (_: IOException) {
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