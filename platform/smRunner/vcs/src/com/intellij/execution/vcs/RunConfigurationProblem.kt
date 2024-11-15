// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vcs

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.checkin.CommitProblemWithDetails

internal class RunConfigurationMultipleProblems(val problems: List<FailureDescription>) : CommitProblem {
  override val text: @NlsSafe String
    get() = problems.sortedBy {
      when (it) {
        is FailureDescription.TestsFailed -> 0
        is FailureDescription.ProcessNonZeroExitCode -> 1
        is FailureDescription.FailedToStart -> 2
      }
    }.joinToString("\n") { it.message }
}

internal class RunConfigurationProblemWithDetails(val problem: FailureDescription) : CommitProblemWithDetails {
  override val text: String
    get() = problem.message

  override fun showDetails(project: Project) {
    when (problem) {
      is FailureDescription.FailedToStart -> {
        if (problem.configuration != null)
          RunDialog.editConfiguration(project,
                                      problem.configuration,
                                      ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", problem.configuration.name))
        else EditConfigurationsDialog(project).show()
      }
      is FailureDescription.ProcessNonZeroExitCode -> {
        RunDialog.editConfiguration(project, problem.configuration,
                                    ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", problem.configuration.name))
      }
      is FailureDescription.TestsFailed -> {
        val (path, virtualFile) = getHistoryFile(project, problem.historyFileName)
        if (virtualFile != null) {
          AbstractImportTestsAction.doImport(project, virtualFile, ExecutionEnvironment.getNextUnusedExecutionId())
        }
        else {
          LOG.error("File not found: $path")
        }
      }
    }
  }

  override val showDetailsAction: String
    get() =
      if (problem is FailureDescription.TestsFailed) ExecutionBundle.message("commit.checks.run.configuration.failed.show.details.action")
      else VcsBundle.message("before.commit.run.configuration.failed.edit.configuration")

  private companion object {
    private val LOG = thisLogger()
  }
}