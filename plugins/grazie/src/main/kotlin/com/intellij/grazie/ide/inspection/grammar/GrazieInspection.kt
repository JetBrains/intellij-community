// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.grammar.GrammarChecker
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.ide.inspection.grammar.problem.GrazieProblemDescriptor
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.utils.lazyConfig
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.containers.CollectionFactory

class GrazieInspection : LocalInspectionTool() {
  companion object : GrazieStateLifecycle {
    private var enabledStrategiesIDs: Set<String> by lazyConfig(this::init)
    private var disabledStrategiesIDs: Set<String> by lazyConfig(this::init)

    private var suppression: SuppressingContext by lazyConfig(this::init)
    private var checking: CheckingContext by lazyConfig(this::init)

    override fun init(state: GrazieConfig.State) {
      enabledStrategiesIDs = state.enabledGrammarStrategies
      disabledStrategiesIDs = state.disabledGrammarStrategies
      suppression = state.suppressingContext
      checking = state.checkingContext
    }

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      enabledStrategiesIDs = newState.enabledGrammarStrategies
      disabledStrategiesIDs = newState.disabledGrammarStrategies
      suppression = newState.suppressingContext
      checking = newState.checkingContext

      ProjectManager.getInstance().openProjects.forEach {
        DaemonCodeAnalyzer.getInstance(it).restart()
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.grammar.text")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(holder.file)) return

        val typos = CollectionFactory.createSmallMemoryFootprintSet<Typo>()

        val strategies = LanguageGrammarChecking.getStrategiesForElement(element, enabledStrategiesIDs, disabledStrategiesIDs)

        for (strategy in strategies) {
          val isCheckNeeded = ApplicationManager.getApplication().isUnitTestMode || when (strategy.getContextRootTextDomain(element)) {
            GrammarCheckingStrategy.TextDomain.NON_TEXT -> false
            GrammarCheckingStrategy.TextDomain.LITERALS -> checking.isCheckInStringLiteralsEnabled
            GrammarCheckingStrategy.TextDomain.COMMENTS -> checking.isCheckInCommentsEnabled
            GrammarCheckingStrategy.TextDomain.DOCS -> checking.isCheckInDocumentationEnabled
            GrammarCheckingStrategy.TextDomain.PLAIN_TEXT -> true
          }

          if (isCheckNeeded) typos.addAll(GrammarChecker.check(element, strategy))
        }

        for (typo in typos.asSequence().filterNot { suppression.isSuppressed(it) }) {
          holder.registerProblem(GrazieProblemDescriptor(typo, isOnTheFly))
        }

        super.visitElement(element)
      }
    }
  }
}
