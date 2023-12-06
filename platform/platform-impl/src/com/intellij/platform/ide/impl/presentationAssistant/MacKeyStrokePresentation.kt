// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.MacKeymapUtil
import java.awt.Font
import java.awt.GraphicsEnvironment

private val LOG = Logger.getInstance("presentationAssistant.MacKeyStrokePresentation")

val macKeyStrokesFont by lazy {
  val font = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.minByOrNull { getNonDisplayableMacSymbols(it).size }
  if (font != null) {
    val macSymbols = getNonDisplayableMacSymbols(font)
    if (macSymbols.isNotEmpty()) {
      LOG.warn("The following symbols from Mac shortcuts aren't supported in selected font: ${macSymbols.joinToString { it.first }}")
    }
  }
  font
}

private fun getNonDisplayableMacSymbols(font: Font) =
  MacKeymapUtil::class.java.declaredFields
    .filter { it.type == String::class.java && it.name != "APPLE" }
    .map { Pair(it.name, it.get(null) as String) }
    .filter { font.canDisplayUpTo(it.second) != -1 }
