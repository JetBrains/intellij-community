// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.visitors

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.psi.PsiFile
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigListDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor

data class EditorConfigValueCompletionCollectorInitiator(
  private val childElement: EditorConfigDescribableElement,
  private val results: CompletionResultSet,
  val file: PsiFile
) : EditorConfigDescriptorVisitor {

  override fun visitOption(option: EditorConfigOptionDescriptor): Unit = collectCompletions(option.value)
  override fun visitList(list: EditorConfigListDescriptor): Unit = collectCompletions(list)
  override fun visitPair(pair: EditorConfigPairDescriptor): Unit = collectCompletions(pair.second)

  override fun visitDescriptor(descriptor: EditorConfigDescriptor) {
    descriptor.parent?.accept(this)
  }

  private fun collectCompletions(element: EditorConfigDescriptor) {
    val settings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
    val collector = EditorConfigValueCompletionCollector(results, childElement, settings)
    element.accept(collector)
  }
}
