// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit.codeInspection.HamcrestCommonClassNames.*
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPrimitiveType
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.asSafely
import com.siyeh.ig.junit.JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_ASSERT
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.importMemberOnDemand
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class HamcrestAssertionsConverterInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  var importMemberOnDemand = true

  override fun getOptionsPane() = pane(checkbox("importMemberOnDemand", JUnitBundle.message("jvm.inspections.migrate.assert.to.matcher.option")))

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val matcherFqn = JavaPsiFacade.getInstance(holder.project).findClass(ORG_HAMCREST_MATCHERS, holder.file.resolveScope)?.qualifiedName
      ?: JavaPsiFacade.getInstance(holder.project).findClass(ORG_HAMCREST_CORE_MATCHERS, holder.file.resolveScope)?.qualifiedName
      ?: return PsiElementVisitor.EMPTY_VISITOR

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      HamcrestAssertionsConverterVisitor(holder, matcherFqn, importMemberOnDemand),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
  }
}

private class HamcrestAssertionsConverterVisitor(
  private val holder: ProblemsHolder,
  private val matcherFqn: String,
  private val importMemberOnDemand: Boolean,
) : AbstractUastNonRecursiveVisitor() {
  private fun isBooleanAssert(methodName: String) = methodName == "assertTrue" || methodName == "assertFalse"

  override fun visitCallExpression(node: UCallExpression): Boolean {
    val methodName = node.methodName ?: return true
    if (!JUNIT_ASSERT_METHODS.contains(methodName)) return true
    val method = node.resolveToUElement() ?: return true
    val methodClass = method.getContainingUClass() ?: return true
    if (methodClass.qualifiedName != ORG_JUNIT_ASSERT && methodClass.qualifiedName != JUNIT_FRAMEWORK_ASSERT) return true
    if (isBooleanAssert(methodName)) {
      val args = node.valueArguments
      val resolveScope = node.sourcePsi?.resolveScope ?: return true
      val psiFacade = JavaPsiFacade.getInstance(holder.project)
      if (args.last() is UBinaryExpression && psiFacade.findClass(ORG_HAMCREST_NUMBER_ORDERING_COMPARISON, resolveScope) == null) return true
    }
    val message = JUnitBundle.message("jvm.inspections.migrate.assert.to.matcher.description", "assertThat()")
    holder.registerUProblem(node, message, MigrateToAssertThatQuickFix(matcherFqn, importMemberOnDemand))
    return true
  }

  companion object {
    private val JUNIT_ASSERT_METHODS = listOf(
      "assertArrayEquals",
      "assertEquals", "assertNotEquals",
      "assertSame", "assertNotSame",
      "assertNotNull", "assertNull",
      "assertTrue", "assertFalse"
    )
  }
}

private class MigrateToAssertThatQuickFix(private val matcherClassFqn: String, private val importMemberOnDemand: Boolean) : LocalQuickFix {
  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", "assertThat()")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val call = element.getUastParentOfType<UCallExpression>() ?: return
    val factory = call.getUastElementFactory(project) ?: return
    val methodName = call.methodName ?: return
    val arguments = call.valueArguments.toMutableList()
    val method = call.resolveToUElementOfType<UMethod>() ?: return
    val message = if (TypeUtils.typeEquals(JAVA_LANG_STRING, method.uastParameters.first().type)) {
      arguments.removeFirst()
    } else null
    val (left, right) = when (methodName) {
      "assertTrue", "assertFalse" -> {
        val conditionArgument = arguments.lastOrNull() ?: return
        when (conditionArgument) {
          is UBinaryExpression -> {
            val operator = conditionArgument.operator.normalize(methodName)
            val matchExpression = factory.buildMatchExpression(operator, conditionArgument.rightOperand) ?: return
            conditionArgument.leftOperand to matchExpression
          }
          is UQualifiedReferenceExpression -> {
            val conditionCall = conditionArgument.selector.asSafely<UCallExpression>() ?: return
            val conditionMethodName = conditionCall.methodName ?: return
            val matchExpression = if (methodName.contains("False")) {
              factory.createMatchExpression(
                "not", factory.buildMatchExpression(conditionMethodName, conditionArgument.receiver, conditionCall.valueArguments.first())
              )
            } else {
              factory.buildMatchExpression(conditionMethodName, conditionArgument.receiver, conditionCall.valueArguments.first())
            } ?: return
            conditionArgument.receiver to matchExpression
          }
          else -> return
        }
      }
      "assertEquals", "assertArrayEquals" -> {
        val matchExpression = factory.createMatchExpression("is", arguments.first()) ?: return
        arguments.last() to matchExpression
      }
      "assertNotEquals" -> {
        val matchExpression = factory.createMatchExpression(
          "not", factory.createMatchExpression("is", arguments.first())
        ) ?: return
        arguments.last() to matchExpression
      }
      "assertSame" -> {
        val matchExpression = factory.createMatchExpression("sameInstance", arguments.first()) ?: return
        arguments.last() to matchExpression
      }
      "assertNotSame" -> {
        val matchExpression = factory.createMatchExpression(
          "not", factory.createMatchExpression("sameInstance", arguments.first())
        ) ?: return
        arguments.last() to matchExpression
      }
      "assertNull" -> {
        val matchExpression = factory.createMatchExpression("nullValue") ?: return
        arguments.first() to matchExpression
      }
      "assertNotNull" -> {
        val matchExpression = factory.createMatchExpression("notNullValue") ?: return
        arguments.first() to matchExpression
      }
      else -> return
    }
    if (importMemberOnDemand) {
      val assertThatCall = factory.createAssertThat(listOfNotNull(message, left, right)) ?: return
      val replaced = call.getQualifiedParentOrThis().replace(assertThatCall)?.asSafely<UQualifiedReferenceExpression>() ?: return
      var toImport = replaced
      while (true) {
        val imported = toImport.importMemberOnDemand()?.asSafely<UCallExpression>() ?: return
        toImport = imported.valueArguments.lastOrNull()?.asSafely<UQualifiedReferenceExpression>() ?: return
      }
    }
  }

  private fun UastElementFactory.createAssertThat(params: List<UExpression>): UExpression? {
    val matchAssert = createQualifiedReference(ORG_HAMCREST_MATCHER_ASSERT, null) ?: return null
    return createCallExpression(matchAssert, "assertThat", params, null, UastCallKind.METHOD_CALL)
      ?.getQualifiedParentOrThis()
  }

  private fun UastElementFactory.createMatchExpression(name: String, parameter: UExpression? = null): UExpression? {
    val paramList = if (parameter == null) emptyList() else listOf(parameter)
    val matcher = createQualifiedReference(matcherClassFqn, null) ?: return null
    return createCallExpression(matcher, name, paramList, null, UastCallKind.METHOD_CALL)?.getQualifiedParentOrThis()
  }

  private fun UExpression.isPrimitiveType() = getExpressionType() is PsiPrimitiveType

  private fun UastElementFactory.createIdEqualsExpression(param: UExpression) =
    if (param.isPrimitiveType()) createMatchExpression("is", param) else createMatchExpression("sameInstance", param)

  private fun UastBinaryOperator.inverse(): UastBinaryOperator = when (this) {
    UastBinaryOperator.EQUALS -> UastBinaryOperator.NOT_EQUALS
    UastBinaryOperator.NOT_EQUALS -> UastBinaryOperator.EQUALS
    UastBinaryOperator.IDENTITY_EQUALS -> UastBinaryOperator.IDENTITY_NOT_EQUALS
    UastBinaryOperator.IDENTITY_NOT_EQUALS -> UastBinaryOperator.IDENTITY_EQUALS
    UastBinaryOperator.GREATER -> UastBinaryOperator.LESS_OR_EQUALS
    UastBinaryOperator.LESS -> UastBinaryOperator.GREATER_OR_EQUALS
    UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS
    UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER
    else -> this
  }

  fun UastBinaryOperator.normalize(methodName: String): UastBinaryOperator = if (methodName.contains("False")) inverse() else this

  private fun UastElementFactory.buildMatchExpression(operator: UastBinaryOperator, param: UExpression): UExpression? = when (operator) {
    UastBinaryOperator.EQUALS -> createMatchExpression("is", param)
    UastBinaryOperator.NOT_EQUALS -> createMatchExpression("not", createMatchExpression("is", param))
    UastBinaryOperator.IDENTITY_EQUALS -> createIdEqualsExpression(param)
    UastBinaryOperator.IDENTITY_NOT_EQUALS -> createMatchExpression("not", createIdEqualsExpression(param))
    UastBinaryOperator.GREATER -> createMatchExpression("greaterThan", param)
    UastBinaryOperator.LESS -> createMatchExpression("lessThan", param)
    UastBinaryOperator.GREATER_OR_EQUALS -> createMatchExpression("greaterThanOrEqualTo", param)
    UastBinaryOperator.LESS_OR_EQUALS -> createMatchExpression("lessThanOrEqualTo", param)
    else -> null
  }

  private fun UastElementFactory.buildMatchExpression(methodName: String, receiver: UExpression, param: UExpression): UExpression? {
    return when (methodName) {
      "contains" -> {
        if (receiver.getExpressionType()?.isInheritorOf(JAVA_UTIL_COLLECTION) == true) {
          return createMatchExpression("hasItem", param)
        }
        if (TypeUtils.typeEquals(JAVA_LANG_STRING, param.getExpressionType())) {
          return createMatchExpression("containsString", param)
        }
        return createMatchExpression("contains", param)
      }
      "equals" -> createMatchExpression("is", param)
      else -> null
    }
  }
}