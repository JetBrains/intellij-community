// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.grammar.GrammarChecker
import com.intellij.grazie.ide.inspection.grammar.problem.GrazieProblemDescriptor
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.language.commit.CommitMessageGrammarCheckingStrategy
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.utils.lazyConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.vcs.commit.message.BaseCommitMessageInspection
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile

class GrazieCommitInspection : BaseCommitMessageInspection() {
  companion object : GrazieStateLifecycle {
    private const val TOOL_SHORT_NAME = "GrazieCommit"
    private val strategy = LanguageGrammarChecking.getStrategyByID(CommitMessageGrammarCheckingStrategy.ID)!!
    private var suppression: SuppressingContext by lazyConfig(this::init)

    override fun init(state: GrazieConfig.State) {
      suppression = state.suppressingContext

      ProjectManager.getInstance().openProjects.forEach { project ->
        updateInspectionState(project, state)
      }
    }

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      suppression = newState.suppressingContext
      if (prevState.checkingContext.isCheckInCommitMessagesEnabled == newState.checkingContext.isCheckInCommitMessagesEnabled) return

      ProjectManager.getInstance().openProjects.forEach { project ->
        updateInspectionState(project, newState)
      }
    }

    private fun updateInspectionState(project: Project, state: GrazieConfig.State = GrazieConfig.get()) {
      with(CommitMessageInspectionProfile.getInstance(project)) {
        if (state.checkingContext.isCheckInCommitMessagesEnabled) {
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

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val typos = GrammarChecker.check(listOf(element), strategy)

        for (typo in typos.filterNot { suppression.isSuppressed(it) }) {
          holder.registerProblem(GrazieProblemDescriptor(typo, isOnTheFly))
        }

        super.visitElement(element)
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.commit.text")
}
