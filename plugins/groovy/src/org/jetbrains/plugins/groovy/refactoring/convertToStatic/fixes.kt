// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ConvertToStatic")

package org.jetbrains.plugins.groovy.refactoring.convertToStatic

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.groovy.intentions.style.AddReturnTypeFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic

private const val MAX_FIX_ITERATIONS = 5

fun applyErrorFixes(element: GroovyPsiElement) {
  repeat(MAX_FIX_ITERATIONS) {
    val checker = TypeChecker()
    element.accept(TypeCheckVisitor(checker))
    if (checker.applyFixes() == 0) {
      return
    }
  }
}

fun applyDeclarationFixes(scope: GroovyPsiElement) {
  repeat(MAX_FIX_ITERATIONS) {
    collectReferencedEmptyDeclarations(scope).forEach { element ->
      when (element) {
        is GrMethod -> AddReturnTypeFix.applyFix(scope.project, element)
        is GrVariable -> {
          val psiType = element.typeGroovy ?: return@forEach
          element.setType(psiType)
          element.modifierList?.setModifierProperty(DEF, false)
        }
      }
    }
  }
}

fun collectReferencedEmptyDeclarations(scope: GroovyPsiElement, recursive: Boolean = true): List<PsiElement> {
  val declarationsVisitor = EmptyDeclarationTypeCollector(recursive)
  scope.accept(declarationsVisitor)
  return declarationsVisitor.elements
}

private class TypeCheckVisitor(val checker: TypeChecker) : GroovyRecursiveElementVisitor() {
  override fun visitElement(element: GroovyPsiElement) {
    if (isCompileStatic(element)) {
      element.accept(checker)
    }
    super.visitElement(element)
  }
}

private class EmptyDeclarationTypeCollector(private val recursive: Boolean) : GroovyElementVisitor() {
  val elements = mutableListOf<PsiElement>()

  override fun visitReferenceExpression(referenceExpression: GrReferenceExpression) {
    checkReference(referenceExpression)
    super.visitReferenceExpression(referenceExpression)
  }

  private fun checkReference(referenceExpression: GroovyReference) {
    val resolveResult = referenceExpression.advancedResolve()
    if (!resolveResult.isValidResult) return
    val element = resolveResult.element
    when (element) {
      is GrAccessorMethod -> {
        checkField(element.property)
      }
      is GrField -> {
        checkField(element)
      }
      is LightElement -> return
      is GrMethod -> {
        if (element.isConstructor) return
        element.returnTypeElementGroovy?.let { return }
        elements += element
      }
    }
  }

  private fun checkField(element: GrField) {
    element.declaredType?.let { return }
    val initializer = element.initializerGroovy ?: return
    initializer.type ?: return
    elements += element
  }

  override fun visitElement(element: GroovyPsiElement) {
    if (recursive) {
      element.acceptChildren(this)
    }
  }
}