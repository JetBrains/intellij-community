// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.completion.ModifierLookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.psi.KtFile

internal class K2ModifierLookupElement(delegate: LookupElement, insertModifier: Boolean) : ModifierLookupElement(delegate, insertModifier) {
  override fun handleInsert(context: InsertionContext) {
    val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
    val ktFile = context.file as KtFile
    if (insertModifier) {
      val modifierObjectAsQualifier = "${COMPOSE_MODIFIER_FQN.asString()}."
      val startOffset = context.startOffset
      val endOffset = startOffset + modifierObjectAsQualifier.length
      context.document.insertString(startOffset, modifierObjectAsQualifier)
      context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, endOffset)
      psiDocumentManager.commitAllDocuments()
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
      @OptIn(KaIdeApi::class) shortenReferencesInRange(ktFile, TextRange(startOffset, endOffset))
    }
    if (ktFile.importDirectives.all { it.importedFqName != COMPOSE_MODIFIER_FQN }) {
      ktFile.addImport(COMPOSE_MODIFIER_FQN)
    }
    psiDocumentManager.commitAllDocuments()
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
    super.handleInsert(context)
  }
}
