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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PROPERTY_UTIL_WRITE_BACK_ELEMENT_WITH_TRIMMED_NAME
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.transforms.TransformTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class PropertyUtilTest : TransformTestCase() {
  private val fieldName = GradleNameElement.create("storeFile")

  @Test
  fun testGetFileValueFromSingleArgConstructor() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, true, PropertyUtil.FILE_CONSTRUCTOR_NAME)
    val arg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("0"))
    arg.setValue("path/to/some/file")
    method.addParsedExpression(arg)
    assertThat(PropertyUtil.getFileValue(method), equalTo("path/to/some/file"))
  }

  @Test
  fun testGetFileValueFromEmptyArgConstructor() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, true, PropertyUtil.FILE_CONSTRUCTOR_NAME)
    assertNull(PropertyUtil.getFileValue(method))
  }

  @Test
  fun testGetFileValueFromMultiArgConstructor() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, true, PropertyUtil.FILE_CONSTRUCTOR_NAME)
    val arg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("0"))
    arg.setValue("path/to/some/file")
    val otherArg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("1"))
    otherArg.setValue("file.txt")
    method.addParsedExpression(arg)
    method.addParsedExpression(otherArg)
    assertThat(PropertyUtil.getFileValue(method), equalTo("path/to/some/file/file.txt"))
  }

  @Test
  fun testGetFileValueFromSingleArgMethodCall() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, false, PropertyUtil.FILE_METHOD_NAME)
    val arg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("0"))
    arg.setValue("path/to/some/file")
    method.addParsedExpression(arg)
    assertThat(PropertyUtil.getFileValue(method), equalTo("path/to/some/file"))
  }

  @Test
  fun testGetFileValueFromEmptyArgMethodCall() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, false, PropertyUtil.FILE_METHOD_NAME)
    assertNull(PropertyUtil.getFileValue(method))
  }

  @Test
  fun testGetFileValueFromMultiArgMethodCall() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, false, PropertyUtil.FILE_METHOD_NAME)
    val arg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("0"))
    arg.setValue("path/to/some/file")
    val otherArg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("1"))
    otherArg.setValue("file.txt")
    method.addParsedExpression(arg)
    method.addParsedExpression(otherArg)
    assertThat(PropertyUtil.getFileValue(method), equalTo("path/to/some/file/file.txt"))
  }

  @Test
  fun testWrongMethodNameGetsNull() {
    val method = GradleDslMethodCall(gradleDslFile, null, fieldName, false, "someRandomMethodName")
    val arg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("0"))
    arg.setValue("path/to/some/file")
    val otherArg = GradleDslLiteral(gradleDslFile, GradleNameElement.create("1"))
    otherArg.setValue("file.txt")
    method.addParsedExpression(arg)
    method.addParsedExpression(otherArg)
    assertNull(PropertyUtil.getFileValue(method))
  }

  @Test
  fun testWriteBackElementWithTrimmedName() {
    val literal = createLiteral(name = "android.defaultConfig.applicationId")
    val buildModel = gradleBuildModel
    val defaultConfigBlock = (buildModel.android().defaultConfig() as ProductFlavorModelImpl).dslElement()
    literal.elementType = PropertyType.REGULAR
    if (!isGroovy) {
      literal.setUseAssignment(true)
    }
    defaultConfigBlock.setNewElement(literal)
    literal.setValue("hello")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_UTIL_WRITE_BACK_ELEMENT_WITH_TRIMMED_NAME);

    val applicationId = buildModel.android().defaultConfig().applicationId()
    if (isGroovy) {
      assertThat(applicationId.psiElement!!.parent!!.text, equalTo("applicationId 'hello'"))
    }
    else {
      assertThat(applicationId.psiElement!!.parent!!.text, equalTo("applicationId = \"hello\""))
    }
  }
}