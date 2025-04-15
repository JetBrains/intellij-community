// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.psi.PsiReferenceBase
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.schema.descriptors.containsKey
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.isSubcaseOf

class EditorConfigFlatOptionKeyReference(element: EditorConfigFlatOptionKey) : PsiReferenceBase<EditorConfigFlatOptionKey>(element) {
  override fun resolve(): EditorConfigFlatOptionKey = element
}

private val EditorConfigFlatOptionKey.psiFile: EditorConfigPsiFile?
  get() {
    val file = containingFile as? EditorConfigPsiFile
    return EditorConfigPsiTreeUtil.getOriginalFile(file)
  }

/**
 *  Key A is in potentialParents(B) iff:
 * 1. B is under header that is subcase of header of A
 * 2. A is higher in file hierarchy (or in the same file before B)
 * 3. A and B have equal keys
 */
private fun EditorConfigFlatOptionKey.findPotentialParents(): List<EditorConfigFlatOptionKey> {
  val optionDescriptor = option.getDescriptor(false) ?: return emptyList()
  val psiFile = psiFile ?: return emptyList()
  return EditorConfigPsiTreeUtil
    .findAllParentsFiles(psiFile)
    .asSequence()
    .flatMap { it.sections.asSequence() }
    .flatMap { it.optionList.asSequence() }
    .filter { it.getDescriptor(false) == optionDescriptor }
    .filter { isAllowedToContainParentIn(it.section) }
    .filter { option.section.header isSubcaseOf it.section.header }
    .mapNotNull(EditorConfigOption::getFlatOptionKey)
    .toList()
}

/**
 * Key A is true parent of key B if and only if:
 * 1. A is in potentialParents(B)
 * and
 * 2. for any x in potentialParents(B) except A: A is not in potentialParents(x)
 *
 * (i.e. potential parent that is not potential parent of any other potential parent)
 */
fun EditorConfigFlatOptionKey.findParents(): List<EditorConfigFlatOptionKey> {
  val potentialParents = this.findPotentialParents()
  // potentialParentsToPotentialParentsOfPotentialParents - oh well
  val parentsToParentsOfParents = potentialParents.map { it to it.findPotentialParents() }
  return potentialParents.filter { particularPotentialParent ->
    parentsToParentsOfParents.all { (potentialParent, potentialParentsOfPotentialParent) ->
      potentialParent === particularPotentialParent || particularPotentialParent !in potentialParentsOfPotentialParent
    }
  }
}

/**
 * Most importantly, disallows to have parent in same file downwards
 */
private fun EditorConfigFlatOptionKey.isAllowedToContainParentIn(section: EditorConfigSection) =
  section.containsKey(this)
  && (section.containingFile.virtualFile != this.containingFile.virtualFile || section.textRange.endOffset < this.section.textRange.startOffset)

fun EditorConfigFlatOptionKey.findChildren(): List<EditorConfigFlatOptionKey> {
  val psiFile = psiFile ?: return emptyList()
  val optionDescriptor = option.getDescriptor(false) ?: return emptyList()
  val children = EditorConfigPsiTreeUtil.findAllChildrenFiles(psiFile) + psiFile

  return children
    .asSequence()
    .flatMap { it.sections.asSequence() }
    .flatMap { it.optionList.asSequence() }
    .filter { it.getDescriptor(false) == optionDescriptor }
    .filter(::canBeDistantParentOf)
    .mapNotNull(EditorConfigOption::getFlatOptionKey)
    .filter(::isDistantParentOf)
    .toList()
}

private fun EditorConfigFlatOptionKey.canBeDistantParentOf(child: EditorConfigOption): Boolean =
  child.option.section.header.isSubcaseOf(option.section.header)

private fun EditorConfigFlatOptionKey.isDistantParentOf(child: EditorConfigFlatOptionKey): Boolean =
  child !== this && isDistantParentOfRecursion(child)

private fun EditorConfigFlatOptionKey.isDistantParentOfRecursion(child: EditorConfigFlatOptionKey): Boolean {
  if (child === this) return true
  val parents = child.findParents()
  return parents.any(::isDistantParentOfRecursion)
}
