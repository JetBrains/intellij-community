// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.core

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import dk.brics.automaton.Automaton
import dk.brics.automaton.RegExp
import dk.brics.automaton.RunAutomaton
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPattern
import org.editorconfig.language.util.EditorConfigPresentationUtil
import java.io.File

object EditorConfigAutomatonBuilder {
  private val KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON = Key<CachedValue<RunAutomaton>>("KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON")
  private val KEY_EDITORCONFIG_ELEMENT_AUTOMATON = Key<CachedValue<Automaton>>("KEY_EDITORCONFIG_ELEMENT_AUTOMATON")

  fun getCachedHeaderRunAutomaton(header: EditorConfigHeader): RunAutomaton =
    CachedValuesManager.getCachedValue(header, KEY_EDITORCONFIG_ELEMENT_RUN_AUTOMATON) {
      val runAutomaton = RunAutomaton(getCachedHeaderAutomaton(header))
      CachedValueProvider.Result.create(runAutomaton, header)
    }

  fun getCachedHeaderAutomaton(header: EditorConfigHeader): Automaton =
    CachedValuesManager.getCachedValue(header, KEY_EDITORCONFIG_ELEMENT_AUTOMATON) {
      val text = header.text
      val internalText = text.substring(1, text.length - 1)
      val path = EditorConfigPresentationUtil.path(header)
      val automaton = RegExp(sanitizeGlob(internalText, path)).toAutomaton()
      CachedValueProvider.Result.create(automaton, header)
    }

  fun getCachedPatternAutomaton(pattern: EditorConfigPattern): Automaton =
    CachedValuesManager.getCachedValue(pattern, KEY_EDITORCONFIG_ELEMENT_AUTOMATON) {
      val text = pattern.text
      val path = EditorConfigPresentationUtil.path(pattern)
      val glob = sanitizeGlob(text, path)
      val automaton = RegExp(glob).toAutomaton()
      CachedValueProvider.Result.create(automaton, pattern)
    }

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
    source = EditorConfig.convertGlobToRegEx(source, arrayListOf())
    return source.replace("(?:", "(")
  }
}
