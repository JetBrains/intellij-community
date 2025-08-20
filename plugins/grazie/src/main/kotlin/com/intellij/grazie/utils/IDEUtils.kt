@file:JvmName("IdeUtils")
package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemFix
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.concurrency.currentThreadContext
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

fun ProblemFix.Part.Change.ijRange(): TextRange = TextRange(range.start, range.endExclusive)

//todo migrate to com.intellij.openapi.progress.runBlockingCancellable when we're compatible with its assertions
@Suppress("UnstableApiUsage")
fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null || currentThreadContext() != EmptyCoroutineContext) {
    return runBlockingCancellable(action)
  }
  return runBlocking(block = action)
}

/** Switch from suspending to blocking context */
@Suppress("UnstableApiUsage")
suspend fun <T> blockingContext(action: () -> T): T {
  // Any runBlocking can indirectly sneak into action and then lead to another blockingContext call.
  // Since runBlocking doesn't know anything about IDEA's thread contexts, an assertion will fail.
  // So we have to work around it here :(
  if (currentThreadContext() != EmptyCoroutineContext) return action()
  return action()
}

fun HighlightSeverity.obtainTextAttributes(project: Project): TextAttributesKey {
  val registrar = SeverityRegistrar.getSeverityRegistrar(project)
  return registrar.getHighlightInfoTypeBySeverity(this).attributesKey
}

fun collectOpenedProjects(): Sequence<Project> {
  val projects = ProjectManager.getInstanceIfCreated()?.openProjects ?: emptyArray()
  return projects.asSequence().filter { !it.isDisposed && it.isInitialized }
}

/**
 * Same as [runBlockingModal] but without [ModalTaskOwner].
 */
internal fun <T> runBlockingModalProcess(
  project: Project? = null,
  title: @NlsContexts.DialogTitle String,
  isCancellable: Boolean = true,
  block: suspend CoroutineScope.() -> T,
): T {
  return ProgressManager.getInstance().runProcessWithProgressSynchronously(
    ThrowableComputable { runBlockingCancellable(block) },
    title,
    isCancellable,
    project
  )
}

internal inline fun <R> catching(block: () -> R): Result<R> {
  try {
    return Result.success(block())
  } catch (exception: CancellationException) {
    throw exception
  } catch (exception: ProcessCanceledException) {
    throw exception
  } catch (exception: Throwable) {
    return Result.failure(exception)
  }
}

fun visualizeSpace(s: String): String {
  return s.replace("\n", "⏎")
}