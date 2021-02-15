/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.ext.transforms

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SingleArgumentMethodTransformTest : TransformTestCase() {
  private val methodName = "method"
  private val otherMethodName = "otherMethodName"
  private val transform = SingleArgumentMethodTransform(methodName, "otherMethodName")

  @Test
  fun testConditionOnNull() {
    assertTrue(transform.test(null, gradleDslFile))
  }

  @Test
  fun testConditionOnNoneMethodCall() {
    val inputElement = createLiteral(name = "boo")
    gradleDslFile.setNewElement(inputElement)
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testConditionOnWrongMethodCall() {
    val inputElement = createMethodCall("wrongMethod")
    gradleDslFile.setNewElement(inputElement)
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testConditionOnCorrectMethodCallNoArguments() {
    val inputElement = createMethodCall(methodName)
    gradleDslFile.setNewElement(inputElement)
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testConditionOnWrongMethodCallOneArgument() {
    val inputElement = createMethodCall("wrongMethod")
    gradleDslFile.setNewElement(inputElement)
    inputElement.addParsedExpression(createLiteral())
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testConditionOnOtherMethodCall() {
    val inputElement = createMethodCall(otherMethodName)
    gradleDslFile.setNewElement(inputElement)
    inputElement.addParsedExpression(createLiteral())
    assertTrue(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testConditionOnCorrectMethodCallOneArgument() {
    val inputElement = createMethodCall(methodName)
    gradleDslFile.setNewElement(inputElement)
    inputElement.addParsedExpression(createLiteral())
    assertTrue(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testTransformOnCorrectForm() {
    val inputElement = createMethodCall(methodName)
    gradleDslFile.setNewElement(inputElement)
    val literal = createLiteral()
    inputElement.addParsedExpression(literal)
    assertThat(transform.transform(inputElement), equalTo(literal as GradleDslElement))
  }

  @Test
  fun testTransformOnMapForm() {
    val inputElement = createMethodCall(methodName)
    gradleDslFile.setNewElement(inputElement)
    val expressionMap = GradleDslExpressionMap(inputElement, GradleNameElement.create("unusedListName"))
    inputElement.addParsedExpression(expressionMap)
    assertThat(transform.transform(inputElement), equalTo(expressionMap as GradleDslElement))
  }

  @Test
  fun testBindCreatesNewElement() {
    val inputElement = null
    val boundElement = transform.bind(gradleDslFile, inputElement, true, "statementName")
    val resultElement = transform.replace(gradleDslFile, inputElement, boundElement, "statementName") as GradleDslMethodCall
    assertThat(resultElement.name, equalTo("statementName"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.value as Boolean, equalTo(true))
    assertThat(argumentElement.name, equalTo(""))
    assertThat(argumentElement.parent?.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindReplacesMapFormCreateNewElement() {
    val inputElement = createMethodCall(methodName)
    gradleDslFile.setNewElement(inputElement)
    val expressionMap = GradleDslExpressionMap(inputElement, GradleNameElement.empty())
    inputElement.addParsedExpression(expressionMap)
    val boundElement = transform.bind(gradleDslFile, inputElement, "32", "statementName")
    val resultElement = transform.replace(gradleDslFile, inputElement, boundElement, "statementName") as GradleDslMethodCall
    // Method call element name doesn't change, we result the same element
    assertThat(resultElement.name, equalTo("unusedStatement"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.value as String, equalTo("32"))
    assertThat(argumentElement.name, equalTo(""))
    assertThat(argumentElement.parent?.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindElementReplacesArgumentValue() {
    val inputElement = createMethodCall(methodName, "statement")
    gradleDslFile.setNewElement(inputElement)
    val literal = createLiteral("")
    literal.setValue(78)
    inputElement.addParsedExpression(literal)
    val boundElement = transform.bind(gradleDslFile, inputElement, "32", "newName")
    val resultElement = transform.replace(gradleDslFile, inputElement, boundElement, "newName") as GradleDslMethodCall
    assertThat(resultElement, sameInstance(inputElement))
    // Method call element name doesn't change, we are re-using the element
    assertThat(resultElement.name, equalTo("statement"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.value as String, equalTo("32"))
    // Name is kept form the literal.
    assertTrue(argumentElement.nameElement.isEmpty)
    assertThat(argumentElement.parent?.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindReferenceReplaceArgumentElement() {
    val inputElement = createMethodCall(methodName, "statement")
    gradleDslFile.setNewElement(inputElement)
    val literal = createLiteral(parent = inputElement)
    literal.setValue("Hello")
    inputElement.addParsedExpression(literal)
    val boundElement = transform.bind(gradleDslFile, inputElement, ReferenceTo("prop"), "newName")
    val resultElement = transform.replace(gradleDslFile, inputElement, boundElement, "newName") as GradleDslMethodCall
    assertThat(resultElement, sameInstance(inputElement))
    // Method call element name doesn't change, we are re-using the element instance
    assertThat(resultElement.name, equalTo("statement"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.referenceText as String, equalTo("prop"))
    // Name is kept form the literal.
    assertTrue(argumentElement.nameElement.isEmpty)
    assertThat(argumentElement.parent?.parent as GradleDslMethodCall, equalTo(resultElement))
  }
}