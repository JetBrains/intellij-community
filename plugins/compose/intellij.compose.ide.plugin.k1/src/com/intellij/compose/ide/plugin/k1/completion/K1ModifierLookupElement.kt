// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k1.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.completion.ModifierLookupElement
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.psi.KtFile

internal class K1ModifierLookupElement(delegate: LookupElement, insertModifier: Boolean) : ModifierLookupElement(delegate, insertModifier) {
  override fun handleInsert(context: InsertionContext) {
    val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
    // Compose plugin inserts Modifier if completion character is '\n', doesn't happened with
    // '\t'. Looks like a bug.
    if (insertModifier && context.completionChar != '\n') {
      context.document.insertString(context.startOffset, CALL_ON_MODIFIER_OBJECT)
      context.offsetMap.addOffset(
        CompletionInitializationContext.START_OFFSET,
        context.startOffset + CALL_ON_MODIFIER_OBJECT.length,
      )
      psiDocumentManager.commitAllDocuments()
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
    }
    val ktFile = context.file as KtFile
    val modifierDescriptor =
      ktFile.resolveImportReference(COMPOSE_MODIFIER_FQN).singleOrNull()
    modifierDescriptor?.let {
      ImportInsertHelper.getInstance(context.project).importDescriptor(ktFile, it)
    }
    psiDocumentManager.commitAllDocuments()
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
    super.handleInsert(context)
  }
}