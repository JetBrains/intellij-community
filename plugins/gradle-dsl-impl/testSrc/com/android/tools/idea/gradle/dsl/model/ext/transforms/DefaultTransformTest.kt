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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test

class DefaultTransformTest : TransformTestCase() {
  private val transform = DefaultTransform()

  @Test
  fun testConditionOnNoneNull() {
    val dslElement = createLiteral()
    assertTrue(transform.test(dslElement, gradleDslFile))
  }

  @Test
  fun testConditionOnNull() {
    assertTrue(transform.test(null, gradleDslFile))
  }

  @Test
  fun testTransformReturnsInput() {
    val dslElement = createLiteral()
    assertThat(transform.transform(dslElement), equalTo(dslElement as GradleDslElement))
  }

  @Test
  fun testBindReuseLiteral() {
    val inputElement = createLiteral()
    val resultElement = transform.bind(gradleDslFile, inputElement, "32", "unused") as GradleDslLiteral
    assertThat(resultElement.value as String, equalTo("32"))
    assertThat(resultElement.nameElement, equalTo(inputElement.nameElement))
  }

  @Test
  fun testBindReuseReference() {
    val inputElement = createLiteral()
    inputElement.setValue(ReferenceTo("fakeRef"))
    val resultElement = transform.bind(gradleDslFile, inputElement, ReferenceTo("prop"), "unused") as GradleDslLiteral
    assertThat(resultElement.referenceText, equalTo("prop"))
    assertThat(resultElement.nameElement, equalTo(inputElement.nameElement))
  }

  @Test
  fun testBindCreateReferenceReuseName() {
    val inputElement = createLiteral()
    val resultElement = transform.bind(gradleDslFile, inputElement, ReferenceTo("prop"), "unused") as GradleDslLiteral
    assertThat(resultElement.referenceText, equalTo("prop"))
    assertThat(resultElement.nameElement, equalTo(inputElement.nameElement))
  }

  @Test
  fun testBindCreateLiteralReuseName() {
    val inputElement = createLiteral()
    inputElement.setValue(ReferenceTo("fakeRef"))
    val resultElement = transform.bind(gradleDslFile, inputElement, true, "unused") as GradleDslLiteral
    assertThat(resultElement.value as Boolean, equalTo(true))
    assertThat(resultElement.nameElement, equalTo(inputElement.nameElement))
  }

  @Test
  fun testNewElementCreateName() {
    val inputElement = null
    val resultElement = transform.bind(gradleDslFile, inputElement, 32, "newName") as GradleDslLiteral
    assertThat(resultElement.value as Int, equalTo(32))
    assertThat(resultElement.nameElement.fullName(), equalTo("newName"))
  }
}