// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.util.containers.CollectionFactory
import java.util.*

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

  private val CHECKED_ELEMENTS: Key<Set<PsiElement>> = Key.create("Grazie.Grammar.Checked")

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.grammar.text")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val typos = CollectionFactory.createSmallMemoryFootprintSet<Typo>()

        val strategies = LanguageGrammarChecking.getStrategiesForElement(element, enabledStrategiesIDs, disabledStrategiesIDs)

        for (strategy in strategies) {
          val domain = strategy.getContextRootTextDomain(element)
          val isCheckNeeded = when (domain) {
            GrammarCheckingStrategy.TextDomain.NON_TEXT -> false
            GrammarCheckingStrategy.TextDomain.LITERALS -> checking.isCheckInStringLiteralsEnabled
            GrammarCheckingStrategy.TextDomain.COMMENTS -> checking.isCheckInCommentsEnabled
            GrammarCheckingStrategy.TextDomain.DOCS -> checking.isCheckInDocumentationEnabled
            GrammarCheckingStrategy.TextDomain.PLAIN_TEXT -> true
          }

          if (isCheckNeeded) {
            val roots = strategy.getRootsChain(element)
            require(roots.isNotEmpty()) { "Roots chain MUST contain at least one element (self)" }

            if (!checkIfAlreadyProcessed(roots.first(), session)) {
              val whitespaceTokens = strategy.getWhiteSpaceTokens()
              val rootsWithoutWhitespaces = roots.filter { it.elementType !in whitespaceTokens }

              require(rootsWithoutWhitespaces.all {
                strategy in LanguageGrammarChecking.getStrategiesForElement(element, enabledStrategiesIDs, disabledStrategiesIDs)
              }) { "Chain roots MUST have the same GrammarCheckingStrategy" }

              require(rootsWithoutWhitespaces.all { strategy.getContextRootTextDomain(it) == domain }) {
                "Chain roots must be from the same TextDomain"
              }

              typos.addAll(GrammarChecker.check(roots, strategy))
            }
          }
        }

        for (typo in typos.asSequence().filterNot { suppression.isSuppressed(it) }) {
          holder.registerProblem(GrazieProblemDescriptor(typo, isOnTheFly))
        }

        super.visitElement(element)
      }

      fun checkIfAlreadyProcessed(element: PsiElement, session: LocalInspectionToolSession): Boolean {
        var data: MutableSet<PsiElement>? = session.getUserData(CHECKED_ELEMENTS) as MutableSet<PsiElement>?
        if (data == null) {
          data = HashSet()
          session.putUserData(CHECKED_ELEMENTS, data)
        }
        if (data.contains(element)) return true
        data.add(element)
        return false
      }
    }
  }
}
