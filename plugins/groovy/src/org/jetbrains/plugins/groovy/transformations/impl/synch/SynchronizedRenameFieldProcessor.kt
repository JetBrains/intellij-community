// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGrFieldProcessor

class SynchronizedRenameFieldProcessor : RenameGrFieldProcessor(), DelegatingHeadlessRenamePsiElementProcessor {

  override fun canProcessElement(element: PsiElement): Boolean {
    return super.canProcessElement(element) && getImplicitLockUsages(element as GrField).any()
  }

  override fun renameElement(element: PsiElement,
                             newName: String,
                             usages: Array<out UsageInfo>,
                             listener: RefactoringElementListener?) {
    element as GrField
    val value = GroovyPsiElementFactory.getInstance(element.project).createLiteralFromValue(newName)
    getImplicitLockUsages(element).forEach { anno ->
      anno.setDeclaredAttributeValue(null, value)
    }
    super.renameElement(element, newName, usages, listener)
  }

  /**
   * Dispatches the conflicts through [HeadlessRenamePsiElementProcessor], as
   * [RenameGrFieldProcessor.HeadlessRenameGrFieldProcessor] does.
   */
  override fun findExistingNameConflictsHeadless(element: PsiElement,
                                                 newName: String,
                                                 conflicts: MultiMap<PsiElement, String>,
                                                 allRenames: Map<PsiElement, String>) {
    findExistingNameConflicts(conflicts, allRenames) { HeadlessRenamePsiElementProcessor.processorOf(it) }
  }
}