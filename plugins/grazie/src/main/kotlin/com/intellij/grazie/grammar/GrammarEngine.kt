// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.LinkedSet
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.util.ExceptionUtil
import org.languagetool.JLanguageTool
import org.languagetool.markup.AnnotatedTextBuilder
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
      ClassLoaderUtil.computeWithClassLoader<Set<Typo>, Throwable>(GraziePlugin.classLoader) {
        val annotated = AnnotatedTextBuilder().addText(str).build()
        LangTool.getTool(lang).check(annotated, true, JLanguageTool.ParagraphHandling.NORMAL,
                                     null, JLanguageTool.Mode.ALL, JLanguageTool.Level.PICKY)
          .asSequence()
          .filterNotNull()
          .map { Typo(it, lang, offset) }
          .toCollection(LinkedSet())
      }
    }
    catch (e: Throwable) {
      if (ExceptionUtil.causedBy(e, ProcessCanceledException::class.java)) {
        throw ProcessCanceledException()
      }

      logger.warn("Got exception during check for typos by LanguageTool", e)
      emptySet()
    }
  }
}
