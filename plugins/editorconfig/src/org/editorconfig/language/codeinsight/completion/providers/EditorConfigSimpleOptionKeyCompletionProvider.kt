// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.editorconfig.Utils
import org.editorconfig.configmanagement.completion.EditorConfigCompletionWeigher
import org.editorconfig.language.codeinsight.completion.providers.EditorConfigCompletionProviderUtil.createLookupAndCheckDeprecation
import org.editorconfig.language.codeinsight.completion.withSeparatorIn
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.getParentOfType

object EditorConfigSimpleOptionKeyCompletionProvider : EditorConfigCompletionProviderBase() {
  override val destination: PsiElementPattern.Capture<PsiElement> =
    psiElement()
      .afterLeaf(psiElement().andOr(
        psiElement(EditorConfigElementTypes.IDENTIFIER),
        psiElement(EditorConfigElementTypes.R_BRACKET)
      ))
      .withSuperParent(3, EditorConfigSection::class.java)
      .andNot(psiElement().withParent(psiElement(EditorConfigElementTypes.PATTERN)))
      .andNot(psiElement().beforeLeaf(psiElement(EditorConfigElementTypes.COLON)))

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position
    val option = position.parent.parent as EditorConfigOption
    val text = position.text
    val firstChar = text.first().takeUnless { text.startsWith(DUMMY_IDENTIFIER_TRIMMED) }
    val section = position.getParentOfType<EditorConfigSection>() ?: return
    val optionKeys = section.optionList.mapNotNull(EditorConfigOption::getFlatOptionKey)
    val rawCompletionItems = EditorConfigOptionDescriptorManager
      .instance
      .getSimpleKeyDescriptors(false)
      .asSequence()
      .filter { optionKeys.none(it::matches) }
      .flatMap { EditorConfigCompletionProviderUtil.selectSimpleParts(it, firstChar).asSequence() }
      .map(::createLookupAndCheckDeprecation)

    val completionItems = if (needSeparatorAfter(option.nextSibling)) {
      rawCompletionItems.map { it.withSeparatorIn(section.containingFile) }
    }
    else rawCompletionItems
    if (Utils.isFullIntellijSettingsSupport()) {
      val orderedResult = result.withRelevanceSorter(CompletionSorter.emptySorter().weigh(EditorConfigCompletionWeigher()))
      completionItems.forEach (orderedResult::addElement)
    }
    else
      completionItems.forEach(result::addElement)
  }

  private fun needSeparatorAfter(space: PsiElement?) =
    space?.textMatches(" ") != true
}
