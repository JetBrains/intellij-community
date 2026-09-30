// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class RenameGrReflectedMethodProcessor : RenameAliasImportedMethodProcessor(), DelegatingHeadlessRenamePsiElementProcessor {

  override fun canProcessElement(element: PsiElement): Boolean {
    return element is GrMethod && element.reflectedMethods.isNotEmpty()
  }

  override fun isInplaceRenameSupported(): Boolean = false

  override fun substituteElementToRenameHeadless(element: PsiElement): PsiElement? = substituteElementToRename(element, null, false)
}