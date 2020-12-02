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
package com.android.tools.idea.gradle.dsl.model.util

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.replaceElement
import com.android.tools.idea.gradle.dsl.model.ext.transforms.TransformTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ReplaceElementTest : TransformTestCase() {
  @Test
  fun testReplaceElement() {
    val holder = createMethodCall("methodName")
    gradleDslFile.setNewElement(holder)
    val oldElement = createLiteral()
    holder.addParsedExpression(oldElement)
    val newElement = createLiteral()
    newElement.setValue(ReferenceTo("fakeRef"))
    replaceElement(holder, oldElement, newElement)
    assertThat(holder.arguments.size, equalTo(1))
    assertThat(holder.arguments, hasItem(newElement))
    assertThat(newElement.parent, equalTo(holder.argumentsElement as GradleDslElement))
    assertThat(newElement.parent?.parent, equalTo(holder as GradleDslElement))
  }

  @Test
  fun testReplaceElementDifferentNames() {
    val holder = createMethodCall("methodName")
    gradleDslFile.setNewElement(holder)
    val oldElement = createLiteral("literal")
    holder.addParsedExpression(oldElement)
    val newElement = createLiteral("reference")
    newElement.setValue(ReferenceTo("fakeRef"))
    try {
      replaceElement(holder, oldElement, newElement)
      fail()
    }
    catch (e: AssertionError) {
      // Expected
    }
  }

  @Test
  fun testReplaceNullAddsElement() {
    val holder = createMethodCall("methodName")
    gradleDslFile.setNewElement(holder)
    val newElement = createLiteral()
    replaceElement(holder, null, newElement)
    assertThat(holder.arguments.size, equalTo(1))
    assertThat(holder.arguments, hasItem(newElement))
    assertThat(newElement.parent, equalTo(holder.argumentsElement as GradleDslElement))
    assertThat(newElement.parent?.parent, equalTo(holder as GradleDslElement))
  }
}