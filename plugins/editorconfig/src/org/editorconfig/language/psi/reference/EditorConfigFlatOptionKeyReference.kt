// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.EditorConfigVfsUtil
import org.editorconfig.language.util.isSubcaseOf

class EditorConfigFlatOptionKeyReference(element: EditorConfigFlatOptionKey)
  : PsiPolyVariantReferenceBase<EditorConfigFlatOptionKey>(element) {
  private val virtualFile: VirtualFile get() = myElement.containingFile.virtualFile
  private val project: Project get() = myElement.project
  private val option get() = myElement.option
  private val section get() = option.section
  private val psiFile: EditorConfigPsiFile?
    get() {
      val file = myElement.containingFile as? EditorConfigPsiFile
      return EditorConfigPsiTreeUtil.getOriginalFile(file)
    }

  /**
   *  Key A is in potentialParents(B) iff:
   * 1. B is under header that is subcase of header of A
   * 2. A is higher in file hierarchy (or in the same file before B)
   * 3. A and B have equal keys
   */
  private fun findPotentialParents(): List<EditorConfigFlatOptionKey> {
    val optionDescriptor = option.getDescriptor(false) ?: return emptyList()
    val currentFileDescriptor = EditorConfigVirtualFileDescriptor(virtualFile)
    val sortedParentFiles = EditorConfigVfsUtil.getEditorConfigFiles(project)
      .asSequence()
      .filter(currentFileDescriptor::isChildOf)
      .sortedBy(currentFileDescriptor::distanceToParent)

    val manager = PsiManager.getInstance(project)
    val firstRoot = sortedParentFiles.indexOfFirst {
      val psiFile = manager.findFile(it) as? EditorConfigPsiFile
      psiFile?.hasValidRootDeclaration ?: false
    }

    val actualParentFiles =
      if (firstRoot < 0) sortedParentFiles
      else sortedParentFiles.take(firstRoot + 1)

    return actualParentFiles
      .mapNotNull { manager.findFile(it) as? EditorConfigPsiFile }
      .flatMap { it.sections.asSequence() }
      .flatMap { it.optionList.asSequence() }
      .filter { it.getDescriptor(false) == optionDescriptor }
      .filter { isAllowedToContainParentIn(it.section) }
      .filter { section.header.isSubcaseOf(it.section.header) }
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
  private fun findParents(): List<EditorConfigFlatOptionKey> {
    val potentialParents = findPotentialParents()
    // potentialParentsToPotentialParentsOfPotentialParents - oh well
    val parentsToParentsOfParents = potentialParents.map { it to it.reference.findPotentialParents() }
    return potentialParents.filter { particularPotentialParent ->
      parentsToParentsOfParents.all { (potentialParent, potentialParentsOfPotentialParent) ->
        potentialParent === particularPotentialParent || particularPotentialParent !in potentialParentsOfPotentialParent
      }
    }
  }

  /**
   * Most importantly, disallows to have parent in same file downwards
   */
  private fun isAllowedToContainParentIn(section: EditorConfigSection) =
    section.containsKey(myElement)
    && (section.containingFile.virtualFile != virtualFile || section.textRange.endOffset < this.section.textRange.startOffset)

  override fun multiResolve(incompleteCode: Boolean) =
    findParents()
      .map(::EditorConfigFlatOptionKeyResolveResult)
      .toTypedArray()

  fun resolveChildren(): Array<EditorConfigFlatOptionKeyResolveResult> {
    val psiFile = psiFile ?: return emptyArray()
    val optionDescriptor = option.getDescriptor(false) ?: return emptyArray()
    val children = EditorConfigPsiTreeUtil.findAllChildrenFiles(psiFile) + psiFile

    return children
      .asSequence()
      .flatMap { it.sections.asSequence() }
      .flatMap { it.optionList.asSequence() }
      .filter { it.getDescriptor(false) == optionDescriptor }
      .filter(::canBeDistantParentOf)
      .mapNotNull(EditorConfigOption::getFlatOptionKey)
      .filter(::isDistantParentOf)
      .map(::EditorConfigFlatOptionKeyResolveResult)
      .toList()
      .toTypedArray()
  }

  private fun canBeDistantParentOf(child: EditorConfigOption) =
    child.option.section.header.isSubcaseOf(option.section.header)

  private fun isDistantParentOf(child: EditorConfigFlatOptionKey) =
    child !== myElement && isDistantParentOfRecursion(child)

  private fun isDistantParentOfRecursion(child: EditorConfigFlatOptionKey): Boolean {
    if (child === myElement) return true
    val parents = child.reference.multiResolve(false).map(EditorConfigFlatOptionKeyResolveResult::getElement)
    return parents.any(::isDistantParentOfRecursion)
  }
}
