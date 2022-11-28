// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.toml.getVersions
import org.jetbrains.plugins.gradle.toml.navigation.refKeyValuePattern
import org.jetbrains.plugins.gradle.toml.navigation.versionRefPattern
import org.toml.lang.psi.TomlKeyValue

@SuppressWarnings("unused")
/**
 * Already implemented in AS
 */
class VersionCatalogCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, rawElementPattern, RefCompletionProvider(true))
    extend(CompletionType.BASIC, inQuotesElementPattern, RefCompletionProvider(false))
  }
}

/**
 * Detects `version.ref = <caret>`
 */
private val rawElementPattern: PsiElementPattern.Capture<PsiElement> = psiElement().withAncestor(3, psiElement(TomlKeyValue::class.java).afterSiblingSkipping(psiElement(PsiWhiteSpace::class.java), refKeyValuePattern))

/**
 * Detects `version.ref = "<caret>"`
 */
private val inQuotesElementPattern: PsiElementPattern.Capture<PsiElement> = psiElement().withParent(versionRefPattern)

private class RefCompletionProvider(val addQuotation: Boolean) : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val element = parameters.position
    val versions = getVersions(element)
    for (version in versions) {
      var lookupElement = LookupElementBuilder.create(version).withTypeText(version.parent?.parent?.asSafely<TomlKeyValue>()?.value?.text)
      if (addQuotation) {
        lookupElement = lookupElement.withInsertHandler { insertionContext, item ->
          val editor = insertionContext.editor
          val name = item.lookupString
          with (editor.caretModel) {
            EditorModificationUtil.insertStringAtCaret(editor, "\"")
            val currentOffset = offset
            moveToOffset(currentOffset - name.length - 1)
            EditorModificationUtil.insertStringAtCaret(editor, "\"")
            moveToOffset(currentOffset + 1)
          }
        }
      }
      result.addElement(lookupElement)
    }
  }

}