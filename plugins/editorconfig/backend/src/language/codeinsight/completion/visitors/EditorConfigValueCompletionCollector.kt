// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.visitors

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValuePair
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.util.containers.Stack
import org.editorconfig.language.codeinsight.completion.providers.EditorConfigCompletionProviderUtil.createLookupAndCheckDeprecation
import org.editorconfig.language.codeinsight.completion.withSuffix
import org.editorconfig.language.schema.descriptors.*
import org.editorconfig.language.schema.descriptors.impl.*
import org.editorconfig.language.util.EditorConfigIdentifierUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.hasParentOfType

class EditorConfigValueCompletionCollector(
  private val results: CompletionResultSet,
  private val childElement: EditorConfigDescribableElement,
  settings: CommonCodeStyleSettings
) : EditorConfigDescriptorVisitor {
  private val cachedMappings = collectDescriptorMappings(childElement, childElement.option)
  private val insertSuffixes: Stack<String> = Stack()

  override fun visitOption(option: EditorConfigOptionDescriptor): Nothing = throw IllegalStateException()

  override fun visitList(list: EditorConfigListDescriptor) {
    if (list.allowRepetitions) {
      insertSuffixes.push(comma)
      list.children.forEach { it.accept(this) }
      insertSuffixes.pop()
    }
    else {
      val presentListElements = (cachedMappings[list] as? EditorConfigOptionValueList)?.optionValueIdentifierList ?: emptyList()
      val unusedDescriptors = list.children.filter { presentListElements.none(it::matches) }
      if (unusedDescriptors.size == 1) {
        val shouldInsertColon = list.isLeftInPair() && !childElement.hasParentOfType<EditorConfigOptionValuePair>()
        if (shouldInsertColon) insertSuffixes.push(colon)
        unusedDescriptors.single().accept(this)
        if (shouldInsertColon) insertSuffixes.pop()
      }
      else {
        insertSuffixes.push(comma)
        unusedDescriptors.forEach { it.accept(this) }
        insertSuffixes.pop()
      }
    }
  }

  override fun visitReference(reference: EditorConfigReferenceDescriptor): Unit =
    EditorConfigIdentifierUtil
      .findDeclarations(childElement.section, reference.id)
      .asSequence()
      .distinctBy(EditorConfigDescribableElement::getText)
      .map(::createLookupAndCheckDeprecation)
      .forEach(results::addElement)

  override fun visitConstant(constant: EditorConfigConstantDescriptor) {
    val element = createLookupAndCheckDeprecation(constant)
    val elementWithSuffix =
      if (insertSuffixes.isEmpty()) element
      else element.withSuffix(insertSuffixes.peek())
    results.addElement(elementWithSuffix)
  }

  override fun visitUnion(union: EditorConfigUnionDescriptor): Unit =
    union.children.forEach { it.accept(this) }

  override fun visitPair(pair: EditorConfigPairDescriptor) {
    if (childElement.getDescriptor(true)?.isRightInPair() == true) {
      pair.second.accept(this)
    }
    else {
      insertSuffixes.push(colon)
      pair.first.accept(this)
      insertSuffixes.pop()
    }
  }

  private val comma: String = when (settings.SPACE_BEFORE_COMMA to settings.SPACE_AFTER_COMMA) {
    true to true -> " , "
    true to false -> " ,"
    false to true -> ", "
    false to false -> ","
    else -> throw IllegalStateException()
  }

  private val colon: String = when (settings.SPACE_BEFORE_COLON to settings.SPACE_AFTER_COLON) {
    true to true -> " : "
    true to false -> " :"
    false to true -> ": "
    false to false -> ":"
    else -> throw IllegalStateException()
  }
}
