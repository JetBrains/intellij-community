// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.core

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.Key
import com.intellij.psi.util.*
import dk.brics.automaton.Automaton
import dk.brics.automaton.BasicOperations
import dk.brics.automaton.RegExp
import dk.brics.automaton.RunAutomaton
import org.editorconfig.configmanagement.GlobVisibilityWorkaround
import org.editorconfig.core.EditorConfigAutomatonBuilder.unionOptimized
import org.editorconfig.language.psi.*
import org.editorconfig.language.util.EditorConfigPresentationUtil
import org.editorconfig.language.util.isValidGlob
import java.io.File

object EditorConfigAutomatonBuilder {
  private val KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON = Key<CachedValue<RunAutomaton>>("KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON")
  private val KEY_EDITORCONFIG_ELEMENT_AUTOMATON = Key<CachedValue<Automaton>>("KEY_EDITORCONFIG_ELEMENT_AUTOMATON")

  /**
   * [header] is is assumed to be valid
   * (i.e. header.isValidGlob)
   *
   * Resulting [RunAutomaton] should be used with *absolute* paths
   */
  fun getCachedHeaderRunAutomaton(header: EditorConfigHeader): RunAutomaton =
    CachedValuesManager.getCachedValue(header, KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON) {
      val runAutomaton = RunAutomaton(getCachedHeaderAutomaton(header))
      CachedValueProvider.Result.create(runAutomaton, header)
    }

  /**
   * [header] is is assumed to be valid
   * (i.e. header.isValidGlob)
   *
   * Resulting [RunAutomaton] should be used with *absolute* paths.
   */
  fun getCachedHeaderAutomaton(header: EditorConfigHeader): Automaton =
    CachedValuesManager.getCachedValue(header, KEY_EDITORCONFIG_ELEMENT_AUTOMATON) {
      val text = header.text
      Log.assertTrue(header.isValidGlob, "builder was given invalid header: $text")
      Log.assertTrue(text.length >= 3, "builder was given a header of insufficient length: $text")
      val path = EditorConfigPresentationUtil.path(header)
      val automaton = globToAutomaton(header.pattern, path)
      CachedValueProvider.Result.create(automaton, header)
    }

  /**
   * [pattern] is is assumed to belong to a valid header
   * (i.e. pattern.header.isValidGlob)
   * {A, B, C}
   */
  fun getCachedPatternAutomaton(pattern: EditorConfigPattern): Automaton =
    CachedValuesManager.getCachedValue(pattern, KEY_EDITORCONFIG_ELEMENT_AUTOMATON) {
      val text = pattern.text
      val header = pattern.header
      Log.assertTrue(header.isValidGlob, "builder was given a pattern in invalid header: $text in ${header.text}")
      Log.assertTrue(header.textLength >= 3, "builder was given a pattern in header of insufficient length: $text in ${header.text}")
      val path = EditorConfigPresentationUtil.path(pattern)
      val automaton = globToAutomaton(pattern, path)
      CachedValueProvider.Result.create(automaton, pattern)
    }

  // TODO except for the last line, this is taken from EditorConfig.filenameMatches
  fun sanitizeGlob(text: String, path: String): String {
    var source = text.replace(File.separatorChar, '/')
    source = source.replace("\\\\#", "#")
    source = source.replace("\\\\;", ";")
    val separator = source.indexOf("/")
    if (separator >= 0) {
      source = path.replace(File.separatorChar, '/') + "/" + if (separator == 0) source.substring(1) else source
    }
    else {
      source = "**/$source"
    }
    source = GlobVisibilityWorkaround.globToRegEx(source)
    return source.replace("(?:", "(")
  }

  /**
   * @param path Directory containing the .editorconfig file from which [header] originates
   */
  fun globToAutomaton(pattern: EditorConfigPattern?, path: String): Automaton {
    if (pattern == null) return RegExp("").toAutomaton()
    var prefix = "**/"
    val separator = pattern.text.indexOf("/")
    if (separator >= 0) {
      prefix = path.replace(File.separatorChar, '/').trimEnd('/') + if (separator == 0) "" else "/"
    }
    return EditorConfigAutomatonBuilderVisitor.buildAutomatonFrom(prefix, pattern)
  }

  private val Log = logger<EditorConfigAutomatonBuilder>()

  /**
   * Creates union of [Automaton]s. Equivalent to [BasicOperations.union] and subsequent [Automaton.minimize], but performs better with
   * certain pathological cases.
   * @see [EditorConfigInspectionsTest.testHeaderProcessingPerformance]
   */
  fun List<Automaton>.unionOptimized(): Automaton =
    this.reduce { a, b ->
      ProgressIndicatorProvider.checkCanceled()
      BasicOperations.union(a, b).also { it.minimize() }
    }
}

private fun subPatternToBricsRegExp(glob: String): String {
  var source = glob.replace(File.separatorChar, '/')
  source = source.replace("""\\#""", "#")
  source = source.replace("""\\;""", ";")
  source = GlobVisibilityWorkaround.globToRegEx(source)
  return source.replace("(?:", "(")
}

private class EditorConfigAutomatonBuilderVisitor private constructor(val prefix: String, val fileText: String) : EditorConfigVisitor() {
  private val accumulator = mutableListOf<Automaton>()

  private fun String.prefixIfAtStart() = if (accumulator.isEmpty()) prefix + this else this

  override fun visitPattern(o: EditorConfigPattern) {
    val range = o.textRange
    accumulator.add(RegExp(subPatternToBricsRegExp(fileText.substring(range.startOffset, range.endOffset).prefixIfAtStart())).toAutomaton())
  }

  override fun visitConcatenatedPattern(o: EditorConfigConcatenatedPattern) {
    val accStart = accumulator.size
    // GlobVisibilityWorkaround.globToRegEx sometimes looks ahead, so it is important not to split the glob when it is not necessary
    val patterns = o.patternList

    // accumulate sub-automatons
    var nonEnumSubPatternsFirst = 0
    for (i in patterns.indices) {
      val pattern = patterns[i]
      if (pattern !is EditorConfigEnumerationPattern) continue

      // first, create automaton for all concatenated patterns before this enumeration
      val nonEnumSubPatternsLast = i - 1
      if (nonEnumSubPatternsFirst <= nonEnumSubPatternsLast) {
        val start = patterns[nonEnumSubPatternsFirst].startOffset
        val end = patterns[nonEnumSubPatternsLast].endOffset
        val nonEnumSubPatterns = fileText.substring(start, end)
        accumulator.add(RegExp(subPatternToBricsRegExp(nonEnumSubPatterns.prefixIfAtStart())).toAutomaton())
      }

      // second, create automaton for the enumeration pattern
      visitEnumerationPattern(pattern)
      nonEnumSubPatternsFirst = i + 1
    }

    // create automaton for remaining non-enumeration patterns
    val nonEnumSubPatternsLast = patterns.size - 1
    if (nonEnumSubPatternsFirst <= nonEnumSubPatternsLast) {
      val start = patterns[nonEnumSubPatternsFirst].startOffset
      val end = patterns[nonEnumSubPatternsLast].endOffset
      val nonEnumSubPatterns = fileText.substring(start, end)
      accumulator.add(RegExp(subPatternToBricsRegExp(nonEnumSubPatterns.prefixIfAtStart())).toAutomaton())
    }

    // reduce
    val toConcatenate = accumulator.subList(accStart, accumulator.size)
    val concatenated = BasicOperations.concatenate(toConcatenate).also { it.minimize() }
    repeat(toConcatenate.size) { accumulator.removeLast() }
    accumulator.add(concatenated)
  }

  override fun visitEnumerationPattern(o: EditorConfigEnumerationPattern) {
    val patterns = o.patternList
    if (patterns.size <= 1) {
      visitPattern(o)
      return
    }

    val prefixAutomatonAdded = if (accumulator.isEmpty()) {
      accumulator.add(RegExp(subPatternToBricsRegExp(prefix)).toAutomaton())
      true
    } else {
      false
    }

    // accumulate sub-automatons
    val accStart = accumulator.size
    patterns.forEach { it.accept(this) }

    // reduce
    val toUnite = accumulator.subList(accStart, accumulator.size)
    val united = toUnite.unionOptimized()
    repeat(toUnite.size) { accumulator.removeLast() }
    val toAdd = if (prefixAutomatonAdded)
      BasicOperations.concatenate(accumulator.removeLast(), united).also { it.minimize() }
    else
      united
    accumulator.add(toAdd)
  }

  companion object {
    fun buildAutomatonFrom(prefix: String, o: EditorConfigPattern): Automaton {
      return EditorConfigAutomatonBuilderVisitor(prefix, o.containingFile!!.text).returningVisit(o)
    }
  }
  private fun returningVisit(o: EditorConfigPattern): Automaton {
    accumulator.clear()
    o.accept(this)
    return accumulator.single()
  }
}