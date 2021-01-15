// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyWriteReference
import org.jetbrains.plugins.groovy.lang.resolve.impl.getExpressionArguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveConstructor
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getAssignmentExpectedType
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getAssignmentOrReturnExpectedType
import org.jetbrains.plugins.groovy.lang.typing.box
import org.jetbrains.plugins.groovy.lang.typing.getWritePropertyType

class GrLiteralConstructorReference(element: GrListOrMap) : GrConstructorReference<GrListOrMap>(element) {

  override fun getRangeInElement(): TextRange = TextRange.EMPTY_RANGE
  override fun handleElementRename(newElementName: String): PsiElement = element
  override fun bindToElement(element: PsiElement): PsiElement = element

  override fun doResolveClass(): GroovyResolveResult? {
    val literal: GrListOrMap = element
    val cs = isCompileStatic(literal)
    val lType: PsiClassType = getExpectedType(literal, cs) as? PsiClassType ?: return null
    val resolveResult: ClassResolveResult = lType.resolveGenerics()
    val clazz: PsiClass = resolveResult.element ?: return null
    val fallsBackToConstructor = if (cs) {
      fallsBackToConstructorCS(clazz, literal)
    }
    else {
      fallsBackToConstructor(clazz, literal)
    }
    if (fallsBackToConstructor) {
      return BaseGroovyResolveResult(clazz, literal, substitutor = resolveResult.substitutor)
    }
    else {
      return null
    }
  }

  override val arguments: Arguments? get() = getArguments(element)

  override val supportsEnclosingInstance: Boolean get() = false
}

private fun getExpectedType(literal: GrListOrMap, cs: Boolean): PsiType? {
  return getExpectedTypeFromAssignmentOrReturn(literal, cs)
         ?: getExpectedTypeFromNamedArgument(literal)
         ?: getExpectedTypeFromCoercion(literal)
}

/**
 * @see org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor.addListAssignmentConstructorErrors
 */
private fun getExpectedTypeFromAssignmentOrReturn(literal: GrListOrMap, cs: Boolean): PsiType? {
  return if (cs) {
    val type: PsiType? = getAssignmentExpectedType(literal)
    when {
      literal.isMap || !literal.isEmpty -> type?.box(literal)
      type is PsiClassType && type.resolve()?.isInterface == true -> null
      else -> type
    }
  }
  else {
    getAssignmentOrReturnExpectedType(literal)
  }
}

private fun getExpectedTypeFromNamedArgument(literal: GrListOrMap): PsiType? {
  val namedArgument: GrNamedArgument = literal.parent as? GrNamedArgument ?: return null
  val label: GrArgumentLabel = namedArgument.label ?: return null
  val propertyReference: GroovyPropertyWriteReference = label.constructorPropertyReference ?: return null
  return getWritePropertyType(propertyReference.advancedResolve())
}

private fun getExpectedTypeFromCoercion(literal: GrListOrMap): PsiType? {
  val safeCast: GrSafeCastExpression = literal.parent as? GrSafeCastExpression ?: return null
  if (!resolvesToDGM(safeCast)) {
    return null
  }
  val typeElement: GrClassTypeElement = safeCast.castTypeElement as? GrClassTypeElement ?: return null
  val typeResult: GroovyResolveResult = typeElement.referenceElement.resolve(false).singleOrNull() ?: return null
  if (safeCastFallsBackToCast(literal, typeResult)) {
    return typeElement.type
  }
  else {
    return null
  }
}

private fun resolvesToDGM(safeCast: GrSafeCastExpression): Boolean {
  val method = safeCast.reference.resolve() as? PsiMethod ?: return false
  val containingClass = (method as? GrGdkMethod)?.staticMethod?.containingClass
  return containingClass?.qualifiedName == DEFAULT_GROOVY_METHODS
}

/**
 * @return `true` if [org.codehaus.groovy.runtime.DefaultGroovyMethods.asType]
 * will fall back to [org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToType]
 * inside `[] as X` or `[:] as X` expression, otherwise `false`
 */
private fun safeCastFallsBackToCast(literal: GrListOrMap, classResult: GroovyResolveResult): Boolean {
  val clazz: PsiClass = classResult.element as? PsiClass ?: return false
  if (literal.isMap) {
    return !clazz.isInterface
  }

  if (clazz.qualifiedName in ignoredFqnsInSafeCast) {
    return false
  }

  // new X(literal)
  val constructors = resolveConstructor(clazz, PsiSubstitutor.EMPTY, listOf(ExpressionArgument(literal)), literal)
  if (constructors.isNotEmpty() && constructors.all { it.isApplicable }) {
    return false
  }

  if (InheritanceUtil.isInheritor(clazz, JAVA_UTIL_COLLECTION)) {
    // def x = new X()
    // x.addAll(literal)
    val noArgConstructors = resolveConstructor(clazz, PsiSubstitutor.EMPTY, emptyList(), literal)
    if (noArgConstructors.isNotEmpty() && noArgConstructors.all { it.isApplicable }) {
      return false
    }
  }

  return true
}

/**
 * FQNs skipped in [org.codehaus.groovy.runtime.DefaultGroovyMethods.asType] for [Collection]
 */
private val ignoredFqnsInSafeCast = setOf(
  JAVA_UTIL_LIST,
  JAVA_UTIL_SET,
  JAVA_UTIL_SORTED_SET,
  JAVA_UTIL_QUEUE,
  JAVA_UTIL_STACK,
  JAVA_UTIL_LINKED_LIST,
  JAVA_LANG_STRING
)

private fun fallsBackToConstructorCS(clazz: PsiClass, literal: GrListOrMap): Boolean {
  if (clazz.qualifiedName == JAVA_LANG_CLASS) {
    return false
  }
  val literalClass = (literal.type as? PsiClassType)?.resolve()
  return !InheritanceUtil.isInheritorOrSelf(literalClass, clazz, true)
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
