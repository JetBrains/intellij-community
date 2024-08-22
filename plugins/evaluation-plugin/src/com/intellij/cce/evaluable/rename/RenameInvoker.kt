// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.common.asSuggestion
import com.intellij.cce.evaluable.common.getEditor
import com.intellij.cce.evaluable.common.positionToString
import com.intellij.cce.evaluable.common.readActionInSmartMode
import com.intellij.cce.evaluation.ContextAwareSuggestionsProvider
import com.intellij.cce.evaluation.SuggestionsProvider
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.actions.MLCompletionFeaturesUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import java.util.*

class RenameInvoker(private val project: Project,
                    private val language: Language,
                    private val strategy: RenameStrategy) : FeatureInvoker {

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session = readActionInSmartMode(project) {
    val openedEditor = getEditor(project) ?: throw IllegalStateException("No open editor")

    LOG.info("Call rename. Expected text: $expectedText. ${positionToString(openedEditor)}")
    val lookup = getSuggestions(expectedText, openedEditor)
    return@readActionInSmartMode createSession(offset, expectedText, properties, lookup)
  }

  override fun comparator(generated: String, expected: String, ): Boolean {
    return expected == generated
  }

  private fun callRename(expectedText: String, editor: Editor): Lookup {
    val start = System.currentTimeMillis()

    val dataContext = buildDataContext(editor)
    val anActionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    RenameElementAction().actionPerformed(anActionEvent)

    val activeLookup = LookupManager.getActiveLookup(editor)
    var suggestions = listOf<Suggestion>()
    var resultFeatures = Features.EMPTY
    if (activeLookup != null) {
      val lookup = activeLookup as LookupImpl
      val features = MLCompletionFeaturesUtil.getCommonFeatures(lookup)
      resultFeatures = Features(
        CommonFeatures(features.context, features.user, features.session),
        lookup.items.map { MLCompletionFeaturesUtil.getElementFeatures(lookup, it).features }
      )
      suggestions = lookup.items.map { it.asSuggestion() }
    }
    val latency = System.currentTimeMillis() - start
    finishSession(expectedText, editor)
    return Lookup.fromExpectedText(expectedText, "", suggestions, latency, resultFeatures, comparator = this::comparator)
  }

  private fun buildDataContext(editor: Editor): DataContext {
    val docManager = PsiDocumentManager.getInstance(project)
    docManager.commitAllDocuments()
    val psiFile = docManager.getPsiFile(editor.document)
    val psiIdentifier = psiFile?.findElementAt(editor.caretModel.offset)
    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
    val psiElement = PsiTreeUtil.getParentOfType(psiIdentifier, PsiNameIdentifierOwner::class.java)

    return DataContext { dataId ->
      when (dataId) {
        "project" -> project
        "editor" -> editor
        "psi.File" -> psiFile
        "psi.Element" -> psiElement
        "virtualFile" -> virtualFile
        "context.Languages" -> arrayOf(com.intellij.lang.Language.ANY)
        else -> null
      }
    }
  }

  private fun getSuggestions(expectedLine: String, editor: Editor): Lookup {
    if (strategy.isDefaultProvider()) {
      return callRename(expectedLine, editor)
    }
    val lang = com.intellij.lang.Language.findLanguageByID(language.ideaLanguageId)
               ?: throw IllegalStateException("Can't find language \"${language.ideaLanguageId}\"")
    val provider = SuggestionsProvider.find(project, strategy.suggestionsProvider)
                   ?: throw IllegalStateException("Can't find suggestions provider \"${strategy.suggestionsProvider}\"")

    return if (provider is ContextAwareSuggestionsProvider) {
      provider.getSuggestions(expectedLine, editor, lang, this::comparator, strategy.collectContextOnly)
    } else {
      provider.getSuggestions(expectedLine, editor, lang, this::comparator)
    }
  }

  private fun createSession(position: Int, expectedText: String, nodeProperties: TokenProperties, lookup: Lookup): Session {
    val sessionUuid = UUID.randomUUID().toString()
    val session = Session(position, expectedText, expectedText.length, nodeProperties, sessionUuid)
    session.addLookup(lookup)
    return session
  }

  private fun finishSession(expectedText: String, editor: Editor) {
    LOG.info("Finish rename. Expected text: $expectedText")
    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return
    lookup.hide()
    InplaceRefactoring.getActiveInplaceRenamer(editor).finish(true)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  companion object {
    private val LOG = logger<RenameInvoker>()
  }
}
