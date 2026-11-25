// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class RenameGrReflectedMethodProcessor : RenameAliasImportedMethodProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return element is GrMethod && element.reflectedMethods.isNotEmpty()
  }

  override fun isInplaceRenameSupported(): Boolean = false
}

/**
 * The rename on the ModPlan engine of a Groovy method with reflected methods, as [RenameGrReflectedMethodProcessor] renames it. Example:
 * `def add(int a, int b = 1)` has the reflected methods `add(int)` and `add(int, int)`. Its rename to `sum` renames the calls of both.
 */
@ApiStatus.Internal
class ModPlanGrReflectedMethodRenameProvider :
  RenameAliasImportedMethodProcessor.ModPlanGrMethodRenameProvider(RenameGrReflectedMethodProcessor::class.java)
