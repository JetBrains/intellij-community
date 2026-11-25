// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.refactoring.rename.ModPlanGrFieldRenameProvider
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGrFieldProcessor

class SynchronizedRenameFieldProcessor : RenameGrFieldProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return super.canProcessElement(element) && getImplicitLockUsages(element as GrField).any()
  }

  override fun renameElement(element: PsiElement,
                             newName: String,
                             usages: Array<out UsageInfo>,
                             listener: RefactoringElementListener?) {
    element as GrField
    getImplicitLockUsages(element).forEach { anno ->
      renameLock(anno, newName)
    }
    super.renameElement(element, newName, usages, listener)
  }
}

/** Gives [annotation], a `@Synchronized` annotation that uses a field as its lock, the name [newName] of the lock. */
private fun renameLock(annotation: PsiAnnotation, newName: String) {
  val value = GroovyPsiElementFactory.getInstance(annotation.project).createLiteralFromValue(newName)
  annotation.setDeclaredAttributeValue(null, value)
}

/**
 * The rename of a Groovy field that a `@Synchronized` annotation uses as its lock: the annotation gets the new name too. An annotation
 * with no value uses the field `$lock`. It holds no reference to the field, so the search does not find it.
 * ```
 * class Guarded {
 *   final $lock = new Object()
 *   @Synchronized
 *   def foo() {}
 * }
 * // rename $lock to myLock
 * class Guarded {
 *   final myLock = new Object()
 *   @Synchronized('myLock')
 *   def foo() {}
 * }
 * ```
 */
@ApiStatus.Internal
class ModPlanSynchronizedFieldRenameProvider : ModPlanGrFieldRenameProvider(SynchronizedRenameFieldProcessor::class.java) {
  override fun declarationEdits(element: PsiElement, newName: String): List<ModPlanEdit> {
    val lockEdits = (element as? GrField)?.let { field ->
      getImplicitLockUsages(field).map { annotation -> ModPlanEdit.update(annotation) { copy -> renameLock(copy, newName) } }.toList()
    }.orEmpty()
    return lockEdits + super.declarationEdits(element, newName)
  }
}
