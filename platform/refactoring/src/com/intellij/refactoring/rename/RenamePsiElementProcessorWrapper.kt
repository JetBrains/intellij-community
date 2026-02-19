package com.intellij.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

/**
 * RenamePsiElementProcessorBase implementation that delegates all calls to another processor.
 *
 * This is useful when needs to expose rename processor where most
 * of the implementation comes from another processor while at the same a specific subset
 * of the behavior can be overridden.
 */
abstract class RenamePsiElementProcessorWrapper(val processor: RenamePsiElementProcessorBase) : RenamePsiElementProcessorBase() {
  override fun createDialog(project: Project,
                            element: PsiElement,
                            nameSuggestionContext: PsiElement?,
                            editor: Editor?): RenameRefactoringDialog {
    return processor.createDialog(project, element, nameSuggestionContext, editor)
  }

  override fun isToSearchForTextOccurrences(element: PsiElement): Boolean {
    return processor.isToSearchForTextOccurrences(element)
  }

  override fun isToSearchInComments(element: PsiElement): Boolean {
    return processor.isToSearchInComments(element)
  }

  override fun canProcessElement(element: PsiElement): Boolean {
    return processor.canProcessElement(element)
  }

  override fun forcesShowPreview(): Boolean {
    return processor.forcesShowPreview()
  }

  override fun createUsageInfo(element: PsiElement, ref: PsiReference, referenceElement: PsiElement): UsageInfo {
    return processor.createUsageInfo(element, ref, referenceElement)
  }

  override fun isInplaceRenameSupported(): Boolean {
    return processor.isInplaceRenameSupported
  }

  override fun hashCode(): Int {
    return processor.hashCode()
  }

  override fun findCollisions(element: PsiElement,
                              newName: String,
                              allRenames: MutableMap<out PsiElement, String>,
                              result: MutableList<UsageInfo>) {
    processor.findCollisions(element, newName, allRenames, result)
  }

  override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? {
    return processor.getElementToSearchInStringsAndComments(element)
  }

  override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
    processor.findExistingNameConflicts(element, newName, conflicts)
  }

  override fun getHelpID(element: PsiElement?): String? {
    return processor.getHelpID(element)
  }

  override fun findExistingNameConflicts(element: PsiElement,
                                         newName: String,
                                         conflicts: MultiMap<PsiElement, String>,
                                         allRenames: MutableMap<PsiElement, String>) {
    processor.findExistingNameConflicts(element, newName, conflicts, allRenames)
  }

  override fun findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): Collection<PsiReference> {
    return processor.findReferences(element, searchScope, searchInCommentsAndStrings)
  }

  override fun getPostRenameCallback(element: PsiElement, newName: String, elementListener: RefactoringElementListener): Runnable? {
    return processor.getPostRenameCallback(element, newName, elementListener)
  }

  override fun getQualifiedNameAfterRename(element: PsiElement, newName: String, nonJava: Boolean): String? {
    return processor.getQualifiedNameAfterRename(element, newName, nonJava)
  }

  override fun getTextOccurrenceSearchStrings(element: PsiElement, newName: String): Pair<String, String>? {
    return processor.getTextOccurrenceSearchStrings(element, newName)
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    processor.prepareRenaming(element, newName, allRenames)
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
    processor.prepareRenaming(element, newName, allRenames, scope)
  }

  override fun showRenamePreviewButton(psiElement: PsiElement): Boolean {
    return processor.showRenamePreviewButton(psiElement)
  }

  override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
    processor.renameElement(element, newName, usages, listener)
  }

  override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
    processor.setToSearchForTextOccurrences(element, enabled)
  }

  override fun toString(): String {
    return processor.toString()
  }

  override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
    processor.setToSearchInComments(element, enabled)
  }

  override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
    return processor.substituteElementToRename(element, editor)
  }

  override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<in PsiElement>) {
    processor.substituteElementToRename(element, editor, renameCallback)
  }

  override fun equals(other: Any?): Boolean {
    return processor.equals(other)
  }

  override fun findReferences(element: PsiElement): MutableCollection<PsiReference> {
    return processor.findReferences(element)
  }

  override fun findReferences(element: PsiElement, searchInCommentsAndStrings: Boolean): MutableCollection<PsiReference> {
    return processor.findReferences(element, searchInCommentsAndStrings)
  }
}