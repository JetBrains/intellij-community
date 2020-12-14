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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslGlobalValue
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class RepositoryClosureTransformTest : TransformTestCase() {
  private val fieldName = "fieldName"
  private val transform: RepositoryClosureTransform by lazy {
    RepositoryClosureTransform(fieldName)
  }

  @Test
  fun testTestNotNull() {
    val inputElement = createMethodCall("methodName")
    assertTrue(transform.test(inputElement))
  }

  @Test
  fun testTestNull() {
    assertFalse(transform.test(null))
  }

  @Test
  fun testTransformMapNotation() {
    val inputElement = createExpressionMap()
    val name = createLiteral(fieldName)
    name.setValue("name")
    val url = createLiteral("url")
    url.setValue("some.url")
    inputElement.setNewElement(name)
    inputElement.setNewElement(url)
    val result = transform.transform(inputElement)
    assertThat(result, sameInstance(name as GradleDslElement))
  }

  @Test
  fun testTransformClosureExists() {
    val inputElement = createMethodCall("methodName")
    val closure = createClosure(inputElement)
    inputElement.setParsedClosureElement(closure)
    val name = createLiteral(fieldName)
    name.setValue("someValue")
    closure.setNewElement(name)
    val result = transform.transform(inputElement)
    assertThat(result, sameInstance(name as GradleDslElement))
  }

  @Test
  fun testTransformNoClosure() {
    val inputElement = createMethodCall("methodName")
    val result = transform.transform(inputElement)
    assertNull(result)
  }

  @Test
  fun testBindWithGlobalValue() {
    val inputElement = createMethodCall("methodCall")
    val resultElement = transform.bind(gradleDslFile, inputElement, "some.value", "unusedName")
    assertThat(resultElement, instanceOf(GradleDslLiteral::class.java))
    assertThat((resultElement as GradleDslLiteral).value as String, equalTo("some.value"))
    assertThat(resultElement.name, equalTo(fieldName))
  }

  @Test
  fun testBindWithExistingElement() {
    val inputElement = createMethodCall("methodName")
    val closure = createClosure(inputElement)
    inputElement.setParsedClosureElement(closure)
    val name = createLiteral(fieldName)
    name.setValue("someValue")
    closure.setNewElement(name)
    val result = transform.bind(gradleDslFile, inputElement, "newValue", "usuedName") as GradleDslLiteral
    assertThat(result, sameInstance(name))
    assertThat(result.value as String, equalTo("newValue"))
    assertThat(result.name, equalTo(fieldName))
  }

  @Test
  fun testReplaceWithMapNotation() {
    val inputElement = createExpressionMap()
    val name = createLiteral(fieldName)
    name.setValue("defaultValue")
    val url = createLiteral("url")
    url.setValue("some.url")
    inputElement.setNewElement(name)
    inputElement.setNewElement(url)
    val newElement = createLiteral(fieldName)
    newElement.setValue("someValue")
    val result = transform.replace(gradleDslFile, inputElement, newElement, "unusedName") as GradleDslExpressionMap
    assertThat(result, sameInstance(inputElement))
    assertSize(2, result.allPropertyElements)
    assertThat(result.getPropertyElement("url"), sameInstance(url as GradleDslElement))
    val newNameElement = result.getPropertyElement(fieldName)
    assertThat(newNameElement, not(sameInstance(name as GradleDslElement)))
    assertThat(newNameElement, sameInstance(newElement as GradleDslElement))
    assertThat((newNameElement as GradleDslLiteral).value as String, equalTo("someValue"))
    assertThat(newNameElement.name, equalTo(fieldName))
  }

  @Test
  fun testReplaceExistingClosure() {
    val inputElement = createMethodCall("methodName")
    val closure = createClosure(inputElement)
    inputElement.setParsedClosureElement(closure)
    val name = createLiteral(fieldName)
    name.setValue("defaultValue")
    closure.setNewElement(name)
    val newElement = createLiteral(fieldName)
    newElement.setValue("someValue")
    val result = transform.replace(gradleDslFile, inputElement, newElement, "unusedName")
    assertThat(result, sameInstance(inputElement as GradleDslElement))
    val resultClosure = result.closureElement!!
    assertThat(resultClosure, sameInstance(closure as GradleDslElement))
    val resultName = resultClosure.getPropertyElement(fieldName)
    assertThat((resultName as GradleDslLiteral).value as String, equalTo("someValue"))
    assertThat(resultName.name, equalTo(fieldName))
  }

  @Test
  fun replaceCreatesClosure() {
    val inputElement = createMethodCall("methodName")
    val newElement = createLiteral(fieldName)
    newElement.setValue("someValue")
    val result = transform.replace(gradleDslFile, inputElement, newElement, "unusedName")
    assertThat(result, sameInstance(inputElement as GradleDslElement))
    val resultClosure = result.closureElement
    assertThat(resultClosure, not(nullValue()))
    val resultName = resultClosure!!.getPropertyElement(fieldName)
    assertThat((resultName as GradleDslLiteral).value as String, equalTo("someValue"))
    assertThat(resultName.name, equalTo(fieldName))
  }
}