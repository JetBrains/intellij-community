// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks

import com.intellij.psi.*
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UExpression
import kotlin.math.max

private const val ORG_TESTNG_ASSERT: @NonNls String = "org.testng.Assert"

private const val ORG_TESTING_ASSERTJUNIT: @NonNls String = "org.testng.AssertJUnit"

private fun parameterOrder(containingClass: PsiClass?, messageOnFirstPosition: Boolean): AbstractAssertHint.ParameterOrder = when {
  // strictly speaking testng fail() has the message on the first position, but we ignore that here
  containingClass != null && ORG_TESTNG_ASSERT == containingClass.qualifiedName -> AbstractAssertHint.ParameterOrder.ACTUAL_EXPECTED_MESSAGE
  messageOnFirstPosition -> AbstractAssertHint.ParameterOrder.MESSAGE_EXPECTED_ACTUAL
  else -> AbstractAssertHint.ParameterOrder.EXPECTED_ACTUAL_MESSAGE
}

private fun isMessageOnFirstPosition(method: PsiMethod): Boolean {
  val containingClass = method.containingClass ?: return false
  val qualifiedName = containingClass.qualifiedName
  return ORG_TESTING_ASSERTJUNIT == qualifiedName ||
         ORG_TESTNG_ASSERT == qualifiedName && "fail" == method.name ||
         JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT == qualifiedName ||
         JUnitCommonClassNames.ORG_JUNIT_ASSERT == qualifiedName ||
         JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE == qualifiedName ||
         JUnitCommonClassNames.ORG_JUNIT_ASSUME == qualifiedName
}

private fun isMessageOnLastPosition(method: PsiMethod): Boolean {
  val containingClass = method.containingClass ?: return false
  val qualifiedName = containingClass.qualifiedName
  return ORG_TESTNG_ASSERT == qualifiedName && "fail" != method.name ||
         JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS == qualifiedName ||
         JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS == qualifiedName
}

/**
 * @param parameter parameter to check
 * @return true if given parameter type looks like an assertion message
 */
private fun isAssertionMessage(parameter: PsiParameter): Boolean {
  val type = parameter.type
  return type.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
         type.equalsToText(CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER + "<" + CommonClassNames.JAVA_LANG_STRING + ">")
}

abstract class AbstractAssertHint<E> {
  enum class ParameterOrder {
    /** Junit 3/Junit 4 */
    MESSAGE_EXPECTED_ACTUAL,

    /** Junit 5 */
    EXPECTED_ACTUAL_MESSAGE,

    /** Testng */
    ACTUAL_EXPECTED_MESSAGE
  }

  abstract val argIndex: Int

  abstract val parameterOrder: ParameterOrder

  abstract val message: E?

  abstract val method: PsiMethod

  abstract val originalExpression: E

  abstract val firstArgument: E

  abstract val secondArgument: E

  val isAssertTrue: Boolean get() = "assertTrue" == method.name

  val expected: E get() = if (parameterOrder != ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  val actual: E get() = if (parameterOrder == ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  val isMessageOnFirstPosition: Boolean get() = parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  val isExpectedActualOrder: Boolean get() = parameterOrder == ParameterOrder.EXPECTED_ACTUAL_MESSAGE ||
                                             parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  /**
   * @param expression argument to assertEquals-like method (either expected or actual value)
   * @return other argument (either actual or expected); null if the supplied expression is neither expected, nor actual value
   */
  fun getOtherExpression(expression: E): E? = when(expression) {
    firstArgument -> secondArgument
    secondArgument -> firstArgument
    else -> null
  }


  companion object {
    @JvmField
    val ASSERT_METHOD_2_PARAMETER_COUNT: @NonNls Map<String, Int> = mapOf(
        "assertArrayEquals" to 2,
        "assertEquals" to 2,
        "assertNotEquals" to 2,
        "assertFalse" to 1,
        "assumeFalse" to 1,
        "assertNotNull" to 1,
        "assertNotSame" to 2,
        "assertNull" to 1,
        "assertSame" to 2,
        "assertThat" to 2,
        "assertTrue" to 1,
        "assumeTrue" to 1,
        "fail" to 0,
        "assertEqualsNoOrder" to 2 //testng
    )
  }
}

class AssertHint private constructor(
  override val argIndex: Int,
  override val parameterOrder: ParameterOrder,
  override val message: PsiExpression?,
  override val method: PsiMethod,
  override val originalExpression: PsiExpression
) : AbstractAssertHint<PsiExpression>() {
  override val firstArgument: PsiExpression get() = getArgument(argIndex)

  override val secondArgument: PsiExpression get() = getArgument(argIndex + 1)

  private fun getArgument(index: Int): PsiExpression =
    (originalExpression as PsiMethodCallExpression).argumentList.expressions[index] as PsiExpression

  companion object {
    @JvmStatic
    fun createAssertEqualsHint(expression: PsiMethodCallExpression): AssertHint? =
      create(expression) { methodName -> if ("assertEquals" == methodName) 2 else null }

    @JvmStatic
    fun createAssertNotEqualsHint(expression: PsiMethodCallExpression): AssertHint? =
      create(expression) { methodName -> if ("assertNotEquals" == methodName) 2 else null }

    @JvmStatic
    fun createAssertTrueFalseHint(expression: PsiMethodCallExpression): AssertHint? =
      create(expression) { methodName -> if ("assertTrue" == methodName || "assertFalse" == methodName) 1 else null }

    @JvmStatic
    fun createAssertSameHint(expression: PsiMethodCallExpression): AssertHint? =
      create(expression) { methodName -> if ("assertSame" == methodName) 2 else null }

    @JvmStatic
    fun createAssertNotSameHint(expression: PsiMethodCallExpression): AssertHint? =
      create(expression) { methodName -> if ("assertNotSame" == methodName) 2 else null }

    @JvmStatic
    fun createAssertEqualsLikeHintForCompletion(
      call: PsiMethodCallExpression?,
      args: Array<PsiExpression?>,
      method: PsiMethod,
      index: Int
    ): AssertHint? {
      if (call == null) return null
      val name = method.name
      if (args.isEmpty()) return null
      val argCount = max(index + 1, args.size)
      if (argCount != 2 && argCount != 3) return null
      if ("assertEquals" != name && "assertNotEquals" != name && "assertSame" != name && "assertNotSame" != name) return null
      val parameters = method.parameterList.parameters
      if (argCount != parameters.size) return null
      if (argCount == 2) {
        return AssertHint(0, parameterOrder(method.containingClass, false), null, method, call)
      }
      if (isAssertionMessage(parameters[0]) && args.size > 1) {
        return AssertHint(1, parameterOrder(method.containingClass, true), args[0], method, call)
      }
      return if (isAssertionMessage(parameters[2]) && args.size > 2) {
        AssertHint(0, parameterOrder(method.containingClass, false), args[2], method, call)
      } else null
    }

    @JvmStatic
    fun create(expression: PsiMethodCallExpression,  methodNameToParamCount: (String) -> Int?): AssertHint? {
      val methodExpression = expression.methodExpression
      val methodName = methodExpression.referenceName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val resolveResult = expression.resolveMethodGenerics()
      val method = resolveResult.element as PsiMethod?
      if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult) return null
      val messageOnLastPosition = isMessageOnLastPosition(method)
      val messageOnFirstPosition = isMessageOnFirstPosition(method)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameterList = method.parameterList
      val parameters = parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      val argumentList = expression.argumentList
      val arguments = argumentList.expressions
      val argumentIndex: Int
      var message: PsiExpression? = null
      if (messageOnFirstPosition) {
        if (parameters.isNotEmpty() &&
            parameters[0].type.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
            parameters.size > minimumParamCount
        ) {
          argumentIndex = 1
          message = arguments[0]
        }
        else {
          argumentIndex = 0
        }
      }
      else {
        argumentIndex = 0
        if (parameters.size > minimumParamCount && minimumParamCount >= 0) {
          val lastParameterIdx = parameters.size - 1
          //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
          if (parameters[lastParameterIdx].type is PsiClassType) {
            message = arguments[lastParameterIdx]
          }
        }
      }
      val containingClass = method.containingClass
      return AssertHint(argumentIndex, parameterOrder(containingClass, messageOnFirstPosition), message, method, expression)
    }

    @JvmStatic
    fun create(
      methodExpression: PsiMethodReferenceExpression,
      methodNameToParamCount: (String) -> Int?
    ): AssertHint? {
      val methodName = methodExpression.referenceName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val resolveResult = methodExpression.advancedResolve(false)
      val element = resolveResult.element as? PsiMethod ?: return null
      if (element.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult) return null
      val messageOnLastPosition = isMessageOnLastPosition(element)
      val messageOnFirstPosition = isMessageOnFirstPosition(element)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameterList = element.parameterList
      val parameters = parameterList.parameters
      return if (parameters.size != minimumParamCount) {
        null
      }
      else {
        val containingClass = element.containingClass
        AssertHint(0, parameterOrder(containingClass, messageOnFirstPosition), null, element, methodExpression)
      }
    }
  }
}

class UAssertHint private constructor(
  override val argIndex: Int,
  override val parameterOrder: ParameterOrder,
  override val message: UExpression?,
  override val method: PsiMethod,
  override val originalExpression: UExpression
) : AbstractAssertHint<UExpression>() {
  override val firstArgument: UExpression get() = getArgument(argIndex)

  override val secondArgument: UExpression get() = getArgument(argIndex + 1)

  private fun getArgument(index: Int): UExpression = (originalExpression as UCallExpression).valueArguments[index]

  companion object {
    @JvmStatic
    fun create(expression: UCallExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = expression.methodName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = expression.resolve() ?: return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = isMessageOnFirstPosition(method)
      val messageOnLastPosition = isMessageOnLastPosition(method)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameters = method.parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      val arguments = expression.valueArguments
      val argumentIndex: Int
      val message = if (messageOnFirstPosition) {
        if (parameters.isNotEmpty() &&
            parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
            parameters.size > minimumParamCount
        ) {
          argumentIndex = 1
          arguments.first()
        }
        else {
          argumentIndex = 0
          null
        }
      }
      else {
        argumentIndex = 0
        if (parameters.size > minimumParamCount && minimumParamCount >= 0) {
          val lastParameterIdx = parameters.size - 1
          //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
          if (parameters[lastParameterIdx].type is PsiClassType) {
            arguments[lastParameterIdx]
          }
          else null
        }
        else null
      }
      val containingClass = method.containingClass
      return UAssertHint(argumentIndex, parameterOrder(containingClass, messageOnFirstPosition), message, method, expression)
    }

    @JvmStatic
    fun create(refExpression: UCallableReferenceExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = refExpression.callableName
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = refExpression.resolve() ?: return null
      if (method !is PsiMethod) return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = isMessageOnFirstPosition(method)
      val messageOnLastPosition = isMessageOnLastPosition(method)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameterList = method.parameterList
      val parameters = parameterList.parameters
      if (parameters.size != minimumParamCount) return null
      val containingClass = method.containingClass
      return UAssertHint(0, parameterOrder(containingClass, messageOnFirstPosition), null, method, refExpression)
    }
  }
}