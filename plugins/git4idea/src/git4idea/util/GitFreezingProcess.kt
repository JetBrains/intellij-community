// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsFreezingProcess
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

/**
 * @see VcsFreezingProcess
 */
class GitFreezingProcess(project: Project, operationTitle: @Nls String, runnable: Runnable) :
  VcsFreezingProcess(project, GitBundle.message("local.changes.freeze.message.git.operation.prefix", operationTitle), runnable)

suspend fun gitFreezingProcess(project: Project, operationTitle: @Nls String, action: suspend () -> Unit) {
  VcsFreezingProcess.runFreezing(project, GitBundle.message("local.changes.freeze.message.git.operation.prefix", operationTitle), action)
}