/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
