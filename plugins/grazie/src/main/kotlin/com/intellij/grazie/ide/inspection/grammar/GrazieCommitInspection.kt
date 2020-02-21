// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.vcs.commit.message.BaseCommitMessageInspection
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile

class GrazieCommitInspection : BaseCommitMessageInspection() {
  companion object : GrazieStateLifecycle {
    private const val TOOL_SHORT_NAME = "GrazieCommit"
    private val grazie: LocalInspectionTool by lazy { GrazieInspection() }

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      if (prevState.enabledCommitIntegration == newState.enabledCommitIntegration) return

      ProjectManager.getInstance().openProjects.forEach { project ->
        updateInspectionState(project, newState)
      }
    }

    //override fun runActivity(project: Project) = updateInspectionState(project)

    private fun updateInspectionState(project: Project, state: GrazieConfig.State = GrazieConfig.get()) {
      with(CommitMessageInspectionProfile.getInstance(project)) {
        if (state.enabledCommitIntegration) {
          addTool(project, LocalInspectionToolWrapper(GrazieCommitInspection()), null)
          setToolEnabled(TOOL_SHORT_NAME, true, project)
        }
        else {
          if (getToolsOrNull(TOOL_SHORT_NAME, project) != null) setToolEnabled(TOOL_SHORT_NAME, false, project)
          //TODO-tanvd how to remove tool?
        }
      }
    }
  }

  override fun getShortName() = TOOL_SHORT_NAME

  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.find("TYPO") ?: HighlightDisplayLevel.WARNING

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = grazie.buildVisitor(holder, isOnTheFly)

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.commit.text")
}
