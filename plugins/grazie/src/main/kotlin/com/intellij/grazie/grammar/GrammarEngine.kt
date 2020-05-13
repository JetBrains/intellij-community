// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.LinkedSet
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.slf4j.LoggerFactory

object GrammarEngine {
  private val logger = LoggerFactory.getLogger(GrammarEngine::class.java)

  private const val tooBigChars = 50_000

  private fun isGrammarCheckUseless(str: String): Boolean {
    return str.isBlank() || str.length > tooBigChars
  }

  fun getTypos(str: String, offset: Int = 0): Set<Typo> {
    if (isGrammarCheckUseless(str)) return emptySet()

    val lang = LangDetector.getLang(str) ?: return emptySet()

    return try {
      LangTool.getTool(lang).check(str, checkCancelled = { ProgressManager.checkCanceled() })
        .asSequence()
        .filterNotNull()
        .map { Typo(it, lang, offset) }
        .toCollection(LinkedSet())
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      logger.warn("Got exception during check for typos by LanguageTool", e)
      emptySet()
    }
  }
}
