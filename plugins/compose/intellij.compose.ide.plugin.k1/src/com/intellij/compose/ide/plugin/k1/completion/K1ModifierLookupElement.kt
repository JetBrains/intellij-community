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