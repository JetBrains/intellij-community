// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.spellchecker

import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.lang.properties.spellchecker.MnemonicsTokenizer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer
import com.intellij.spellchecker.tokenizer.TokenConsumer
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.PsiUtil.isPluginModule

private val IGNORED_CHARS = setOf('_', '&')

internal class DevKitMnemonicsTokenizer : EscapeSequenceTokenizer<PropertyValueImpl>(), MnemonicsTokenizer {

  override fun hasMnemonics(propertyValue: String): Boolean {
    return propertyValue.contains('_') || propertyValue.contains('&')
  }

  override fun tokenize(element: PropertyValueImpl, consumer: TokenConsumer) {
    val module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module != null && isRelevantModule(module)) {
      val text = element.getText()

      val unescapedText = StringBuilder(text.length)
      val offsets = IntArray(text.length + 1)
      parseStringCharactersWithMnemonics(text, unescapedText, offsets)

      processTextWithOffsets(element, consumer, unescapedText, offsets, 0)
    }
    else {
      consumer.consumeToken(element, PlainTextSplitter.getInstance())
    }
  }

  override fun ignoredCharacters(): Set<Char> = IGNORED_CHARS

  private fun isRelevantModule(module: Module): Boolean {
    return isIntelliJPlatformProject(module.project)
           || isPluginModule(module)
           || PluginModuleType.isOfType(module)
  }

  private fun parseStringCharactersWithMnemonics(
    chars: String,
    out: StringBuilder,
    sourceOffsets: IntArray,
  ): Boolean {
    var index = 0
    val outOffset = out.length
    while (index < chars.length) {
      val c = chars[index++]
      sourceOffsets[out.length - outOffset] = index - 1
      sourceOffsets[out.length + 1 - outOffset] = index
      if (c !in IGNORED_CHARS) {
        out.append(c)
        continue
      }

      if (index == -1) return false
      sourceOffsets[out.length - outOffset] = index
    }
    return true
  }
}
