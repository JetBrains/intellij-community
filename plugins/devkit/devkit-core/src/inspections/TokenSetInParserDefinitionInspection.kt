// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.ParserDefinition
import com.intellij.psi.PsiClassType
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal class TokenSetInParserDefinitionInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun checkClass(uClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaPsi = uClass.javaPsi
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(javaPsi)) return ProblemDescriptor.EMPTY_ARRAY
    if (!InheritanceUtil.isInheritor(javaPsi, ParserDefinition::class.java.name)) return ProblemDescriptor.EMPTY_ARRAY

    val problemsHolder = createProblemsHolder(uClass, manager, isOnTheFly)
    uClass.fields
      .filter { it.isTokenSetField() && it.isIllegal(uClass) }
      .forEach { reportField(it, problemsHolder) }

    return problemsHolder.resultsArray
  }

  private fun UField.isTokenSetField(): Boolean {
    val fieldType = (this.type as? PsiClassType)?.resolve() ?: return false
    return fieldType.qualifiedName == TokenSet::class.java.name
  }

  private fun UField.isIllegal(uClass: UClass): Boolean {
    val initializer = this.uastInitializer
    if (initializer != null) {
      return initializer.containsIllegalReferences()
    }
    else {
      val constructors = uClass.methods.filter { it.isConstructor }
      return constructors.any { it.containsFieldAssignmentWithIllegalReferences(this) } ||
             uClass.initializers.any { it.containsFieldAssignmentWithIllegalReferences(this) } ||
             companionObjectInitBlockContainsIllegalUsage(uClass)
    }
  }

  private fun UExpression.containsIllegalReferences(): Boolean {
    if (this is UResolvable) {
      val resolved = this.resolveToUElement() ?: return false
      when (resolved) {
        is UField -> {
          // TokenSet.EMPTY, TokenSet.ANY, etc. are allowed to use
          return resolved.getContainingUClass()?.qualifiedName != TokenSet::class.java.name
        }
        is UMethod -> {
          // check if the assignment contains any non-platform class usage
          val nonCoreApiFinder = NonCoreApiFinder()
          this.accept(nonCoreApiFinder)
          return nonCoreApiFinder.nonCoreApiUsed
        }
      }
    }
    return false
  }

  private fun reportField(field: UField, problemsHolder: ProblemsHolder) {
    problemsHolder.registerUProblem(field, DevKitBundle.message("inspection.token.set.in.parser.definition"))
  }

  private fun UDeclaration.containsFieldAssignmentWithIllegalReferences(field: UField): Boolean {
    var containsIllegalAssignment = false
    this.accept(object : AbstractUastVisitor() {
      private val checkChildrenFlag = false
      private val skipChildrenFlag = true
      override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        if (containsIllegalAssignment) return skipChildrenFlag
        val resolvedElement = (node.leftOperand as? UReferenceExpression)?.resolveToUElement() ?: return checkChildrenFlag
        if (resolvedElement.sourcePsi?.isEquivalentTo(field.sourcePsi) == true && node.rightOperand.containsIllegalReferences()) {
          containsIllegalAssignment = true
          return skipChildrenFlag
        }
        return checkChildrenFlag
      }
    })
    return containsIllegalAssignment
  }

  private fun UField.companionObjectInitBlockContainsIllegalUsage(uClass: UClass): Boolean {
    val companionInitBlock = uClass.innerClasses.firstOrNull { it.javaPsi.name == "Companion" }
      ?.methods?.firstOrNull { it.javaPsi.name == "Companion" }
    return companionInitBlock?.containsFieldAssignmentWithIllegalReferences(this) == true
  }

  private class NonCoreApiFinder : AbstractUastVisitor() {
    private val checkChildrenFlag = false
    private val skipChildrenFlag = true
    var nonCoreApiUsed = false

    override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
      return node.checkNonCoreApiUsage()
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
      return node.checkNonCoreApiUsage()
    }

    private fun UReferenceExpression.checkNonCoreApiUsage(): Boolean {
      if (nonCoreApiUsed) return skipChildrenFlag
      val resolvedElementContainingClass = this.resolveToUElement()?.getContainingUClass()?.qualifiedName ?: return checkChildrenFlag
      if (resolvedElementContainingClass != TokenSet::class.java.name &&
          resolvedElementContainingClass != TokenType::class.java.name) {
        nonCoreApiUsed = true
        return skipChildrenFlag
      }
      return checkChildrenFlag
    }
  }
}
