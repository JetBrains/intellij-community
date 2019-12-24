// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class GrMapConstructorPropertyReference(element: GrArgumentLabel) : GroovyPropertyWriteReferenceBase<GrArgumentLabel>(element) {

  override val receiverArgument: Argument?
    get() {
      val label: GrArgumentLabel = element
      val namedArgument: GrNamedArgument = label.parent as GrNamedArgument // hard cast because reference must be checked before
      val constructorReference: GroovyConstructorReference = requireNotNull(getConstructorReference(namedArgument))
      val resolveResult: GroovyResolveResult = constructorReference.resolveClass() ?: return null
      val clazz: PsiClass = resolveResult.element as? PsiClass ?: return null
      val type: PsiClassType = JavaPsiFacade.getElementFactory(label.project).createType(clazz, resolveResult.substitutor)
      return JustTypeArgument(type)
    }

  override val propertyName: String get() = requireNotNull(element.name)

  override val argument: Argument? get() = (element.parent as GrNamedArgument).expression?.let(::ExpressionArgument)

  companion object {

    @JvmStatic
    fun getConstructorReference(argument: GrNamedArgument): GroovyConstructorReference? {
      val parent: PsiElement? = argument.parent
      if (parent is GrListOrMap) {
        return parent.constructorReference
      }
      else if (parent is GrArgumentList) {
        val grandParent: PsiElement? = parent.getParent()
        if (grandParent is GrConstructorCall) {
          return grandParent.constructorReference
        }
      }
      return null
    }
  }
}
