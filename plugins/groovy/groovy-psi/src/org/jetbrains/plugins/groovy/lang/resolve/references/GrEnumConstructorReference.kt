// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

class GrEnumConstructorReference(element: GrEnumConstant) : GrConstructorReference<GrEnumConstant>(element) {

  override fun resolveClass(): GroovyResolveResult? {
    return element.containingClass?.let {
      BaseGroovyResolveResult(it, element, ResolveState.initial())
    }
  }

  override val arguments: Arguments?
    get() = if (element.argumentList == null) {
      // Enum constant without argument list (e.g. `A`) is equivalent to default constructor call (e.g. `A()`).
      // One should not return `null` in this case because `null` means arguments cannot be computed.
      emptyList()
    }
    else {
      element.getArguments()
    }

  override fun getRangeInElement(): TextRange = element.nameIdentifierGroovy.textRangeInParent

  override fun handleElementRename(newElementName: String): PsiElement = element

  override fun isReferenceTo(element: PsiElement): Boolean {
    return element is GrMethod && element.isConstructor && element.getManager().areElementsEquivalent(resolve(), element)
  }
}
