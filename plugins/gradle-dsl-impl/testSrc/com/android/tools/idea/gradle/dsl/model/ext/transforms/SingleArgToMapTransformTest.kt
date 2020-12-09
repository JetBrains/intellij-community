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

import com.android.tools.idea.gradle.dsl.model.dependencies.FileTreeDependencyModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SingleArgToMapTransformTest : TransformTestCase() {
  private val dirName = FileTreeDependencyModelImpl.DIR // This transform is tied to this field name.
  private val fieldName = "include"
  private val methodName = "methodName"
  private val transform = SingleArgToMapTransform(dirName, fieldName)

  @Test
  fun testRejectOnNull() {
    assertFalse(transform.test(createLiteral(), gradleDslFile))
  }

  @Test
  fun testRejectOnEmptyMethodCall() {
    assertFalse(transform.test(createMethodCall(methodName), gradleDslFile))
  }

  @Test
  fun testRejectOnMethodCallWithMapArg() {
    val inputElement = createMethodCall(methodName)
    val mapArg = createExpressionMap()
    inputElement.addParsedExpression(mapArg)
    assertFalse(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testAcceptOnMethodCallWithLiteralArg() {
    val inputElement = createMethodCall(methodName)
    val literalArg = createLiteral()
    inputElement.addParsedExpression(literalArg)
    assertTrue(transform.test(inputElement, gradleDslFile))
  }

  @Test
  fun testReplaceWithCorrectElement() {
    val inputElement = createMethodCall(methodName)
    val literalArg = createLiteral("boo")
    literalArg.setValue(true)
    inputElement.addParsedExpression(literalArg)
    val newElement = transform.bindMap(gradleDslFile, inputElement, "unused", true)
    val resultElement = transform.replace(gradleDslFile, inputElement, newElement, "unused") as GradleDslMethodCall
    assertThat(resultElement, sameInstance(inputElement))
    assertSize(1, resultElement.arguments)
    val map = resultElement.arguments[0] as GradleDslExpressionMap
    assertSize(2, map.allPropertyElements)
    val literal = map.getPropertyElement(dirName)!! as GradleDslLiteral
    assertThat(literal.value as Boolean, equalTo(true))
    assertThat(map.getPropertyElement(fieldName) as GradleDslExpression, sameInstance(newElement))
  }
}