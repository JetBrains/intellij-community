// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion


import com.intellij.cce.core.*
import com.intellij.cce.evaluable.common.*
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.actions.MLCompletionFeaturesUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.util.*

class CompletionActionsInvoker(project: Project,
                               language: Language,
                               private val strategy: CompletionStrategy) : BaseCompletionActionsInvoker(project, language) {
  private val commonInvoker: ActionsInvoker = CommonActionsInvoker(project)

  private val completionType = when (strategy.completionType) {
    CompletionType.SMART -> com.intellij.codeInsight.completion.CompletionType.SMART
    else -> com.intellij.codeInsight.completion.CompletionType.BASIC
  }

  private val prefixCreator = when (strategy.prefix) {
    is CompletionPrefix.NoPrefix -> NoPrefixCreator()
    is CompletionPrefix.CapitalizePrefix -> CapitalizePrefixCreator()
    is CompletionPrefix.SimplePrefix -> SimplePrefixCreator((strategy.prefix as CompletionPrefix.SimplePrefix).n)
  }

  private fun createSession(position: Int, expectedText: String, nodeProperties: TokenProperties, lookup: Lookup): Session {
    val sessionUuid = lookup.features?.common?.context?.get(CCE_SESSION_UID_FEATURE_NAME)
                      ?: UUID.randomUUID().toString()
    val session = Session(position, expectedText, expectedText.length, nodeProperties, sessionUuid)
    session.addLookup(lookup)
    return session
  }

  override fun comparator(generated: String, expected: String, ): Boolean {
    return expected == generated
  }

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session = readActionInSmartMode(project) {
    val editor = getEditorSafe(project)
    LOG.info("Call completion. Type: $completionType. ${positionToString(editor)}")
    val prefix = prefixCreator.getPrefix(expectedText)
    if (prefix.isNotEmpty()) {
      commonInvoker.printText(prefix)
    }

    val start = System.currentTimeMillis()
    val isNew = LookupManager.getActiveLookup(editor) == null
    val activeLookup = invokeCompletion(expectedText, prefix, completionType, editor)
    val latency = System.currentTimeMillis() - start
    if (activeLookup == null) {
      commonInvoker.printText(expectedText.substring(prefix.length))
      return@readActionInSmartMode createSession(offset, expectedText, properties,
                                                 Lookup.fromExpectedText(expectedText, prefix, emptyList(), latency,
                                                                         isNew = isNew,
                                                                         startOffset = prefix.length,
                                                                         comparator = this::comparator))
    }

    val lookup = activeLookup as LookupImpl
    val features = MLCompletionFeaturesUtil.getCommonFeatures(lookup)
    val resultFeatures = Features(
      CommonFeatures(features.context, features.user, features.session),
      lookup.items.map { MLCompletionFeaturesUtil.getElementFeatures(lookup, it).features }
    )
    val suggestions = lookup.items.map { it.asSuggestion() }

    val success = finishSession(expectedText, prefix, editor)
    if (!success) {
      commonInvoker.printText(expectedText.substring(prefix.length))
    }
    return@readActionInSmartMode createSession(offset, expectedText, properties,
                                               Lookup.fromExpectedText(expectedText, prefix, suggestions, latency, resultFeatures,
                                                                       isNew, prefix.length, this::comparator))
  }

  private fun finishSession(expectedText: String, prefix: String, editor: Editor): Boolean {
    LOG.info("Finish completion. Expected text: $expectedText")
    if (strategy.completionType == CompletionType.SMART) return false
    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return false
    val expectedItemIndex = lookup.items.indexOfFirst { it.lookupString == expectedText }
    try {
      return if (expectedItemIndex != -1) lookup.finish(expectedItemIndex, expectedText.length - prefix.length) else false
    }
    finally {
      lookup.hide()
    }
  }

  companion object {
    const val CCE_SESSION_UID = "sessionUid"
    const val CCE_SESSION_UID_FEATURE_NAME = "ml_ctx_cce_$CCE_SESSION_UID"
  }
}
