// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import dk.brics.automaton.Automaton
import org.editorconfig.core.EditorConfigAutomatonBuilder
import org.editorconfig.core.EditorConfigAutomatonBuilder.getCachedHeaderAutomaton
import org.editorconfig.language.codeinsight.inspections.hasNumerousWildcards
import org.editorconfig.language.codeinsight.inspections.hasRedundancy
import org.editorconfig.language.codeinsight.inspections.isEmptyHeader
import org.editorconfig.language.psi.EditorConfigEnumerationPattern
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPattern
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.containsErrors

val EditorConfigHeader.isValidGlob: Boolean
  get() {
    if (header.textMatches("[")) return false
    if (containsErrors(header)) return false
    // That is, if closing bracket is missing
    if (nextSibling is PsiErrorElement) return false
    if (header.isEmptyHeader()) return false
    if (header.hasNumerousWildcards()) return false
    if (PsiTreeUtil.findChildrenOfAnyType(header, EditorConfigEnumerationPattern::class.java).any { it.hasRedundancy() }) return false
    return true
  }

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
