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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class MapMethodTransformTest : TransformTestCase() {
  private val methodName = "methodName"
  private val fieldName = "fieldName"
  private val configName = "compile"

  private val transform = MapMethodTransform(methodName, fieldName)

  @Test
  fun testAcceptNull() {
    assertTrue(transform.test(null, gradleDslFile))
  }

  @Test
  fun testRejectLiteral() {
    assertFalse(transform.test(createLiteral(), gradleDslFile))
  }

  @Test
  fun testAcceptNoArgMethodCall() {
    assertTrue(transform.test(createMethodCall(methodName), gradleDslFile))
  }

  @Test
  fun testRejectNonMapArgMethodCall() {
    val inputElement = createMethodCall(methodName)
    val literal = createLiteral(fieldName)
    inputElement.addParsedExpression(literal)
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testAcceptMapArgMethodCall() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    inputElement.addParsedExpression(mapElement)
    assertTrue(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testTransformNullOnEmptyMethodCalL() {
    val inputElement = createMethodCall(methodName)
    assertNull(transform.transform(inputElement))
  }

  @Test
  fun testTransformNullOnEmptyMap() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    inputElement.addParsedExpression(mapElement)
    assertNull(transform.transform(inputElement))
  }

  @Test
  fun testTransformNullOnWrongMapElement() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    val literal = createLiteral("thisIsNotMyFieldName")
    inputElement.addParsedExpression(mapElement)
    mapElement.setParsedElement(literal)
    assertNull(transform.transform(inputElement))
  }

  @Test
  fun testTransformNonNullOnCorrectMapElement() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    val literal = createLiteral(fieldName) as GradleDslElement
    inputElement.addParsedExpression(mapElement)
    mapElement.setParsedElement(literal)
    val resultElement = transform.transform(inputElement)!!
    assertNotNull(resultElement)
    assertThat(resultElement, sameInstance(literal))
  }

  @Test
  fun testTransformNonNullOnMultipleElements() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    val innerMapElement = createExpressionMap(GradleNameElement.create(fieldName)) as GradleDslElement
    val literal = createLiteral("notMyField")
    inputElement.addParsedExpression(mapElement)
    mapElement.setParsedElement(literal)
    mapElement.setParsedElement(innerMapElement)
    val resultElement = transform.transform(inputElement)!!
    assertNotNull(resultElement)
    assertThat(resultElement, sameInstance(innerMapElement))
  }

  @Test
  fun testReplaceNullCreatesMethodCallAndMap() {
    val inputElement = transform.bind(gradleDslFile, null, "value", "unused")
    val resultElement = transform.replace(gradleDslFile, null, inputElement, configName)
    assertThat(resultElement, instanceOf(GradleDslMethodCall::class.java))
    assertThat(resultElement.name, equalTo(configName))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertSize(1, resultElement.arguments)
    assertThat(resultElement.arguments[0], instanceOf(GradleDslExpressionMap::class.java))
    val map = resultElement.arguments[0] as GradleDslExpressionMap
    assertThat(map.name, equalTo(""))
    assertSize(1, map.allPropertyElements)
    val literal = map.allPropertyElements[0] as GradleDslLiteral
    assertThat(literal, sameInstance(inputElement))
  }

  @Test
  fun testReplaceTreeWithNewElement() {
    val inputElement = createMethodCall(methodName)
    val mapElement = createExpressionMap()
    val innerMapElement = createExpressionMap(GradleNameElement.create(fieldName)) as GradleDslElement
    val literal = createLiteral("notMyField") as GradleDslElement
    inputElement.addParsedExpression(mapElement)
    mapElement.setParsedElement(literal)
    mapElement.setParsedElement(innerMapElement)
    val newElement = transform.bind(gradleDslFile, null, "value", "unused")
    val resultElement = transform.replace(gradleDslFile, inputElement, newElement, configName)
    assertThat(resultElement, sameInstance(inputElement))
    assertSize(1, resultElement.arguments)
    val map = resultElement.arguments[0] as GradleDslExpressionMap
    assertThat(map, sameInstance(mapElement))
    assertSize(2, map.allPropertyElements)
    assertThat(map.allPropertyElements, hasItems(literal, newElement))
    assertThat(map.allPropertyElements, not(hasItem(innerMapElement)))
  }
}