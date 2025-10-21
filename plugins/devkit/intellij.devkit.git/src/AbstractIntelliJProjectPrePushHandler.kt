// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PrePushHandler
import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.config.GitSharedSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString

internal abstract class AbstractIntelliJProjectPrePushHandler : PrePushHandler {
  protected abstract fun isAvailable(): Boolean

  private fun handlerIsApplicable(project: Project): Boolean =
    isAvailable() && IntelliJProjectUtil.isIntelliJPlatformProject(project)

  final override fun handle(project: Project, pushDetails: List<PushInfo>, indicator: ProgressIndicator): PrePushHandler.Result {
    if (!handlerIsApplicable(project)) {
      return PrePushHandler.Result.OK
    }

    if (pushDetails.any {
        isTargetBranchProtected(project, it)
        && validate(project, it, indicator) == PushInfoValidationResult.INVALID
      }) {
      return PrePushHandler.Result.ABORT_AND_CLOSE
    }

    return PrePushHandler.Result.OK
  }

  protected open fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean =
    GitSharedSettings.getInstance(project).isBranchProtected(pushInfo.pushSpec.target.presentation)

  protected abstract fun validate(project: Project, info: PushInfo, indicator: ProgressIndicator): PushInfoValidationResult

  protected enum class PushInfoValidationResult {
    VALID, INVALID, SKIP
  }

  companion object {
    @JvmStatic
    protected val ContentRevision.path: Path?
      get() = file.takeIf { !it.isNonLocal }?.ioFile?.toPath() // TODO: handle non-local?

    private val fileExtensionsNotToTrack = setOf("iml", "md")

    @JvmStatic
    protected fun Sequence<Path>.anyIn(paths: List<String>, pathsToIgnore: List<String>): Boolean =
      any { path ->
        val siPath = path.invariantSeparatorsPathString
        path.extension !in fileExtensionsNotToTrack
        && paths.any { siPath.contains(it) }
        && pathsToIgnore.none { siPath.contains(it) }
      }

    @JvmStatic
    protected fun List<VcsFullCommitDetails>.toHtml(): @NlsSafe String =
      joinToString("<br/>") { commit ->
        "${commit.id.toShortString()}: ${commit.subject}"
      }

    internal fun <T> invokeAndWait(modalityState: ModalityState, computable: () -> T): T {
      val ref = AtomicReference<T>()
      ApplicationManager.getApplication().invokeAndWait({ ref.set(computable.invoke()) }, modalityState)
      return ref.get()
    }
  }
}