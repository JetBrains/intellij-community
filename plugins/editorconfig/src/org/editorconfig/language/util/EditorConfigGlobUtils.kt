// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import dk.brics.automaton.Automaton
import org.editorconfig.core.EditorConfigAutomatonBuilder
import org.editorconfig.core.EditorConfigAutomatonBuilder.getCachedHeaderAutomaton
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPattern

infix fun EditorConfigHeader.isSubcaseOf(general: EditorConfigHeader): Boolean {
  if (!this.isValidGlob) return false
  if (!general.isValidGlob) return false
  val subcaseAutomaton = getCachedHeaderAutomaton(this)
  val generalAutomaton = getCachedHeaderAutomaton(general)
  return subcaseAutomaton.subsetOf(generalAutomaton)
}

infix fun EditorConfigHeader.isEquivalentTo(other: EditorConfigHeader): Boolean {
  if (!this.header.isValidGlob) return false
  if (!other.header.isValidGlob) return false
  val thisAutomaton = getCachedHeaderAutomaton(this)
  val otherAutomaton = getCachedHeaderAutomaton(other)
  return thisAutomaton == otherAutomaton
}

infix fun EditorConfigPattern.isSubcaseOf(general: EditorConfigPattern): Boolean {
  if (!general.header.isValidGlob) return false
  val generalAutomaton = EditorConfigAutomatonBuilder.getCachedPatternAutomaton(general)
  return this isSubcaseOf generalAutomaton
}

infix fun EditorConfigPattern.isSubcaseOf(generalAutomaton: Automaton): Boolean {
  if (!header.isValidGlob) return false
  val subcaseAutomaton = EditorConfigAutomatonBuilder.getCachedPatternAutomaton(this)
  return subcaseAutomaton.subsetOf(generalAutomaton)
}

infix fun EditorConfigHeader.matches(string: String): Boolean {
  Log.assertTrue(header.isValidGlob)
  return EditorConfigAutomatonBuilder.getCachedHeaderRunAutomaton(section.header).run(string)
}

infix fun EditorConfigHeader.matches(file: VirtualFile): Boolean {
  Log.assertTrue(header.isValidGlob)
  return matches(file.path)
}

private val Log = logger<EditorConfigHeader>()
