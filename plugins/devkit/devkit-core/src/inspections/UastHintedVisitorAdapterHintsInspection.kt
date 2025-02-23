// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.UastVisitor

private const val UAST_VISITOR_INDEX = 1
private const val UAST_VISITOR_HINT_INDEX = 2
private val UAST_VISITOR_BASE_CLASS_NAMES = setOf(
  "org.jetbrains.uast.visitor.UastVisitor",
  "org.jetbrains.uast.visitor.AbstractUastVisitor",
  "org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor"
)

/**
 * Stores the hint classes and their allowed visited elements.
 * The reason for exceptions is that `UInjectionHost` is an interface that is not
 * a part of `UElement` interfaces hierarchy but is implemented by concrete elements.
 */
private val HINT_EXCEPTIONS = mapOf(
  "org.jetbrains.uast.expressions.UInjectionHost" to setOf("org.jetbrains.uast.ULiteralExpression",
                                                           "org.jetbrains.uast.UPolyadicExpression")
)

internal class UastHintedVisitorAdapterHintsInspection : DevKitUastInspectionBase() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, UastHintedVisitorAdapter::class.java.canonicalName)
  }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
    return UastHintedVisitorAdapter.create(holder.file.getLanguage(), object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (isCreateVisitorAdapterCall(node)) {
          inspectVisitorAndHints(node, holder)
        }
        return true
      }
    }, arrayOf(UCallExpression::class.java))
  }

  private fun isCreateVisitorAdapterCall(expression: UCallExpression): Boolean {
    if (!expression.isMethodNameOneOf(listOf("create"))) return false
    val resolvedMethod = expression.resolve() ?: return false
    val containingClassName = resolvedMethod.containingClass?.qualifiedName ?: return false
    return UastHintedVisitorAdapter.Companion::class.java.canonicalName == containingClassName ||
           UastHintedVisitorAdapter::class.java.canonicalName == containingClassName
  }

  private fun inspectVisitorAndHints(expression: UCallExpression, holder: ProblemsHolder) {
    val expressionSourcePsi = expression.sourcePsi ?: return
    val hintParamValue = expression.getArgumentForParameter(UAST_VISITOR_HINT_INDEX) ?: return
    val hintClassLiterals = getHintClasses(hintParamValue).takeIf { it.isNotEmpty() } ?: return
    val project = holder.project
    val uElementClass = JavaPsiFacade.getInstance(project).findClass(UElement::class.java.name, expressionSourcePsi.resolveScope) ?: return
    val classLiteralAndExpandedClassesList = hintClassLiterals
      .mapNotNull {
        val resolvedClass = (it.type as? PsiClassType)?.resolve() ?: return@mapNotNull null
        val allClasses = resolvedClass.collectUElementInterfaces(uElementClass).toSet()
        ClassLiteralAndExpandedClasses(it, allClasses)
      }

    val visitorParamValue = expression.getArgumentForParameter(UAST_VISITOR_INDEX) ?: return
    val visitorClass = getVisitorClass(visitorParamValue) ?: return
    val uastVisitorMethodNames = getUastVisitorMethodNames(project, expressionSourcePsi)
    val overriddenMethods = visitorClass.javaPsi.allMethods
      .filter { !it.isConstructor }
      .filter { it.name in uastVisitorMethodNames }
      .filter { it.containingClass?.qualifiedName !in UAST_VISITOR_BASE_CLASS_NAMES }
      .mapNotNull { it.toUElement(UMethod::class.java) }

    checkMissingHints(classLiteralAndExpandedClassesList, overriddenMethods, holder)
    checkRedundantHints(classLiteralAndExpandedClassesList, overriddenMethods, holder)
  }

  private fun checkMissingHints(
    classLiteralAndExpandedClassesList: List<ClassLiteralAndExpandedClasses>,
    overriddenMethods: List<UMethod>,
    holder: ProblemsHolder,
  ) {
    val hintClasses = classLiteralAndExpandedClassesList.flatMap { it.coveredClasses }.toSet()
    val methodsNotInHintClasses = overriddenMethods.filter { !it.isReachedByHints(hintClasses) }
    for (redundantMethod in methodsNotInHintClasses) {
      val methodName = redundantMethod.name
      val className = redundantMethod.resolveTheOnlyParameterClass()?.name ?: continue
      holder.registerUProblem(
        redundantMethod,
        DevKitBundle.message("inspection.uast.hinted.visitor.adapter.hints.missing.hint", methodName, className)
      )
    }
  }

  private fun PsiClass.collectUElementInterfaces(uElementClass: PsiClass): Iterable<PsiClass> {
    return ClassInheritorsSearch.search(this, this.useScope, true, true, false)
             .asIterable()
             .filter { it.isInterface }
             .filter { it.isInheritor(uElementClass, true) } + this
  }

  private fun UMethod.isReachedByHints(hintClasses: Set<PsiClass>): Boolean {
    val visitedElementClass = this.resolveTheOnlyParameterClass() ?: return true // shouldn't happen
    val visitedElementClassQualifiedName = visitedElementClass.qualifiedName
    return visitedElementClass in hintClasses ||
           hintClasses.any {
             HINT_EXCEPTIONS[it.qualifiedName]?.contains(visitedElementClassQualifiedName) == true ||
             it.isInheritor(visitedElementClass, true) // visitElement(UElement) is covered by any hint class
           }
  }

  private fun checkRedundantHints(
    classLiteralAndExpandedClassesList: List<ClassLiteralAndExpandedClasses>,
    overriddenMethods: List<UMethod>,
    holder: ProblemsHolder,
  ) {
    val hintClassesNotInMethods = classLiteralAndExpandedClassesList
      .filter { literalAndClasses ->
        !overriddenMethods.any { it.isReachedByHints(literalAndClasses.coveredClasses) }
      }
      .map { it.classLiteral }
    for (hintClassLiteral in hintClassesNotInMethods) {
      val hintClassName = (hintClassLiteral.type as? PsiClassType)?.className ?: continue
      holder.registerUProblem(
        hintClassLiteral,
        DevKitBundle.message("inspection.uast.hinted.visitor.adapter.hints.redundant.hint", hintClassName)
      )
    }
  }

  private fun UMethod.resolveTheOnlyParameterClass(): PsiClass? {
    val parameterType = this.uastParameters.firstOrNull()?.type as? PsiClassType
    return parameterType?.resolve()
  }

  private fun getUastVisitorMethodNames(project: Project, expressionSourcePsi: PsiElement): Set<String> {
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val uastVisitorClass = javaPsiFacade.findClass(UastVisitor::class.java.name, expressionSourcePsi.resolveScope) ?: return emptySet()
    return uastVisitorClass.methods.map { it.name }.toSet()
  }

  private fun getHintClasses(expression: UExpression): List<UClassLiteralExpression> {
    when (expression) {
      is USimpleNameReferenceExpression -> {
        val resolvedHint = expression.resolveToUElement() ?: return emptyList()
        return when (resolvedHint) {
          // support only fields, methods are unlikely to happen
          is UParameter -> emptyList() // unknown hints provided from the outer context
          is UField -> getHintClasses(resolvedHint.uastInitializer as? UCallExpression ?: return emptyList())
          is UVariable -> getHintClasses(resolvedHint.uastInitializer as? UCallExpression ?: return emptyList())
          else -> emptyList()
        }
      }
      is UCallExpression -> {
        if (expression.isNewArrayWithInitializer() || expression.methodName == "arrayOf") {
          return expression.valueArguments
            .mapNotNull {
              when (it) {
                is UQualifiedReferenceExpression -> it.receiver as? UClassLiteralExpression
                else -> it as? UClassLiteralExpression
              }
            }
        }
      }
    }
    return emptyList()
  }

  private fun getVisitorClass(visitorClassExpression: UExpression): UClass? {
    when (visitorClassExpression) {
      is UObjectLiteralExpression -> return visitorClassExpression.declaration
      is UCallExpression -> {
        if (visitorClassExpression.isConstructorCall()) {
          return visitorClassExpression.classReference?.resolveToUElement() as? UClass
        }
      }
      is USimpleNameReferenceExpression -> {
        val resolvedClassHolder = visitorClassExpression.resolveToUElement() ?: return null
        return when (resolvedClassHolder) {
          is UParameter -> null // unknown visitor provided from the outer context
          is UField -> getVisitorClass(resolvedClassHolder.uastInitializer as? UCallExpression ?: return null)
          is UVariable -> getVisitorClass(resolvedClassHolder.uastInitializer as? UCallExpression ?: return null)
          else -> null
        }
      }
    }
    return null
  }

  /**
   * Stores class literal and all `UElement` interfaces it covers.
   */
  private class ClassLiteralAndExpandedClasses(
    val classLiteral: UClassLiteralExpression,
    val coveredClasses: Set<PsiClass>,
  )

}
