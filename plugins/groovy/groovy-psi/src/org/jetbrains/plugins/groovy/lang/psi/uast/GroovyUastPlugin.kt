// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.uast

import com.intellij.lang.Language
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parents
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf

/**
 * This is a very limited implementation of UastPlugin for Groovy,
 * provided only to make Groovy play with UAST-based reference contributors and spring class annotators
 */
class GroovyUastPlugin : UastLanguagePlugin {
  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParent(element, { parent }, requiredType)

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParent(element, { makeUParent(element) }, requiredType)

  private fun convertElementWithParent(element: PsiElement,
                                       parentProvider: () -> UElement?,
                                       requiredType: Class<out UElement>?): UElement? =
    when (element) {
      is GroovyFile -> GrUFile(element, this)
      is GrLiteral -> GrULiteral(element, parentProvider)
      is GrAnnotationNameValuePair -> GrUNamedExpression(element, parentProvider)
      is GrAnnotation -> GrUAnnotation(element, parentProvider)
      is GrTypeDefinition -> GrUClass(element, parentProvider)
      is GrMethod -> GrUMethod(element, parentProvider)
      is GrParameter -> GrUParameter(element, parentProvider)
      is GrQualifiedReference<*> -> GrUReferenceExpression(element, parentProvider)
      is LeafPsiElement -> if (element.elementType == GroovyTokenTypes.mIDENT) LazyParentUIdentifier(element, null) else null
      else -> null
    }?.takeIf { requiredType?.isAssignableFrom(it.javaClass) ?: true }

  private fun makeUParent(element: PsiElement) =
    element.parents(false).mapNotNull { convertElementWithParent(it, null) }.firstOrNull()

  override fun getMethodCallExpression(element: PsiElement,
                                       containingClassFqName: String?,
                                       methodName: String): UastLanguagePlugin.ResolvedMethod? = null //not implemented

  override fun getConstructorCallExpression(element: PsiElement,
                                            fqName: String): UastLanguagePlugin.ResolvedConstructor? = null //not implemented

  override fun isExpressionValueUsed(element: UExpression): Boolean = TODO("not implemented")

  override val priority: Int = 0

  override fun isFileSupported(fileName: String): Boolean = fileName.endsWith(".groovy", ignoreCase = true)

  override val language: Language = GroovyLanguage

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    classSetOf(GroovyPsiElement::class.java)
}

class GrULiteral(private val grElement: GrLiteral, private val parentProvider: () -> UElement?) : ULiteralExpression, UInjectionHost {
  override val value: Any? get() = grElement.value
  override fun evaluate(): Any? = value
  override val uastParent: UElement? by lazy(parentProvider)
  override val psi: PsiElement = grElement
  override val uAnnotations: List<UAnnotation> = emptyList() //not implemented
  override val isString: Boolean
    get() = super<UInjectionHost>.isString
  override val psiLanguageInjectionHost: PsiLanguageInjectionHost
    get() = grElement
}

class GrUNamedExpression(val grElement: GrAnnotationNameValuePair, private val parentProvider: () -> UElement?) : UNamedExpression {
  override val name: String?
    get() = grElement.name
  override val expression: UExpression
    get() = grElement.value.toUElementOfType() ?: GrUnknownUExpression(grElement.value, this)

  override val uastParent: UElement? by lazy(parentProvider)

  override val psi: GrAnnotationNameValuePair = grElement
  override val uAnnotations: List<UAnnotation> = emptyList() //not implemented

  override fun equals(other: Any?): Boolean {
    if (other !is GrUNamedExpression) return false
    return grElement == other.grElement
  }

  override fun hashCode(): Int = grElement.hashCode()
}

class GrUAnnotation(private val grElement: GrAnnotation,
                    private val parentProvider: () -> UElement?) : UAnnotationEx, UAnchorOwner, UMultiResolvable {

  override val javaPsi: PsiAnnotation = grElement

  override val qualifiedName: String?
    get() = grElement.qualifiedName

  override fun resolve(): PsiClass? = grElement.nameReferenceElement?.resolve() as PsiClass?
  override fun multiResolve(): Iterable<ResolveResult> =
    grElement.nameReferenceElement?.multiResolve(false)?.asIterable() ?: emptyList()

  override val uastAnchor: UIdentifier?
    get() = grElement.classReference.referenceNameElement?.let { UIdentifier(it, this) }

  override val attributeValues: List<UNamedExpression> by lazy {
    grElement.parameterList.attributes.map {
      GrUNamedExpression(it, { this })
    }
  }

  override fun findAttributeValue(name: String?): UExpression? = findDeclaredAttributeValue(name)

  override fun findDeclaredAttributeValue(name: String?): UExpression? {
    return grElement.parameterList.attributes.asSequence()
      .find { it.name == name || it.name == null && "value" == name }
      ?.let { GrUNamedExpression(it) { this } }
  }

  override val uastParent: UElement? by lazy(parentProvider)

  override val psi: PsiElement = grElement
}

class GrUnknownUExpression(override val psi: PsiElement?, override val uastParent: UElement?) : UExpression {

  @NonNls
  override fun asLogString(): String = "GrUnknownUExpression(grElement)"

  override val uAnnotations: List<UAnnotation> = emptyList() //not implemented

}