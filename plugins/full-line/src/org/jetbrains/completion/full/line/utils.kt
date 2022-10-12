package org.jetbrains.completion.full.line

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.util.ExceptionUtil
import org.jetbrains.completion.full.line.providers.CloudExceptionWithCustomRegistry
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.*

fun <T> Future<T>.awaitWithCheckCanceled(start: Long, timeout: Int, indicator: ProgressIndicator): T {
  while (true) {
    if (System.currentTimeMillis() - start > timeout) {
      throw TimeoutException("Completion timeout exceeded")
    }
    indicator.checkCanceled()
    try {
      return get(10, TimeUnit.MILLISECONDS)
    }
    catch (ignore: TimeoutException) {
    }
    catch (e: Throwable) {
      val cause = e.cause
      if (cause is ProcessCanceledException) {
        throw (cause as ProcessCanceledException?)!!
      }
      if (cause is CancellationException) {
        throw ProcessCanceledException(cause)
      }
      ExceptionUtil.rethrowUnchecked(e)
      throw RuntimeException(e)
    }
  }
}
// Completion Contributor

fun PsiFile.projectFilePath(): String {
  val projectPath = project.basePath ?: "/"
  val filePath = virtualFile.path

  return filePath.removePrefix(projectPath).removePrefix("/")
}

// Extract correct presentable message from exception.
@NlsSafe
fun Throwable.decoratedMessage(): String {
  return when (this) {
    is SocketTimeoutException, is TimeoutException -> "Connection timeout"
    is UnknownHostException, is SocketException -> "Can't reach completion server"
    is ExecutionException -> {
      when (cause) {
        null -> "Execution exception, no cause.".also {
          Logger.getInstance("FLCC-ExecutionException").error(this)
        }
        is CloudExceptionWithCustomRegistry -> with(cause as CloudExceptionWithCustomRegistry) {
          (cause?.decoratedMessage() ?: "") + "\n With custom registry `${registry.key}` value `${registry.asString()}`"
        }
        else -> with(ExceptionUtil.getRootCause(this)) {
          if (this is ExecutionException) "Execution exception" else decoratedMessage()
        }
      }
    }
    else -> this.localizedMessage
  }
}

fun ProjectManager.currentOpenProject() = openProjects.firstOrNull { it.isOpen }

inline fun <T> measureTimeMillisWithResult(block: () -> T): Pair<Long, T> {
  val start = System.currentTimeMillis()
  val res = block()
  return (System.currentTimeMillis() - start) to res
}
