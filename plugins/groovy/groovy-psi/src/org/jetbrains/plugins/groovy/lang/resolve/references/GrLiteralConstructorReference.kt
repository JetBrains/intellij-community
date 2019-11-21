// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getExpressionArguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getAssignmentOrReturnExpectedType

class GrLiteralConstructorReference(element: GrListOrMap) : GrConstructorReference<GrListOrMap>(element) {

  override fun getRangeInElement(): TextRange = TextRange.EMPTY_RANGE
  override fun handleElementRename(newElementName: String): PsiElement = element
  override fun bindToElement(element: PsiElement): PsiElement = element

  override fun doResolveClass(): GroovyResolveResult? {
    val literal: GrListOrMap = element
    val lType: PsiClassType = getExpectedType(literal) as? PsiClassType ?: return null
    val resolveResult: ClassResolveResult = lType.resolveGenerics()
    val clazz: PsiClass = resolveResult.element ?: return null
    if (fallsBackToConstructor(clazz, literal)) {
      return BaseGroovyResolveResult(clazz, literal, substitutor = resolveResult.substitutor)
    }
    else {
      return null
    }
  }

  override val arguments: Arguments? get() = getArguments(element)

  override val supportsEnclosingInstance: Boolean get() = false
}

private fun getExpectedType(literal: GrListOrMap): PsiType? {
  return getAssignmentOrReturnExpectedType(literal)
}

/**
 * @return `true` if [org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToType]
 * will fall back to [org.codehaus.groovy.runtime.InvokerHelper.invokeConstructorOf], otherwise `false`
 */
private fun fallsBackToConstructor(clazz: PsiClass, literal: GrListOrMap): Boolean {
  if (clazz.isEnum) {
    return false
  }
  val qualifiedName = clazz.qualifiedName ?: return false
  if (qualifiedName in ignoredFqnsInTransformation) {
    return false
  }
  if (InheritanceUtil.isInheritor(clazz, JAVA_LANG_NUMBER)) {
    return false
  }
  val literalClass = (literal.type as? PsiClassType)?.resolve()
  if (InheritanceUtil.isInheritorOrSelf(literalClass, clazz, true)) {
    return false
  }
  if (!literal.isMap) {
    if (qualifiedName == JAVA_UTIL_LINKED_HASH_SET) {
      return false
    }
    if (clazz.hasModifier(JvmModifier.ABSTRACT)) {
      val lhs = JavaPsiFacade.getInstance(literal.project).findClass(JAVA_UTIL_LINKED_HASH_SET, literal.resolveScope)
      if (InheritanceUtil.isInheritor(lhs, qualifiedName)) {
        return false
      }
    }
  }
  return true
}

/**
 * FQNs skipped in [org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToType]
 */
private val ignoredFqnsInTransformation = setOf(
  JAVA_LANG_OBJECT,
  JAVA_LANG_CLASS,
  JAVA_LANG_STRING,
  JAVA_LANG_BOOLEAN,
  JAVA_LANG_CHARACTER
)

private fun getArguments(literal: GrListOrMap): Arguments? {
  if (literal.isMap) {
    return listOf(ExpressionArgument(literal))
  }
  else {
    return getExpressionArguments(literal.initializers)
  }
}
