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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


open class TransformTestCase : GradleFileModelTestCase() {
  protected val gradleDslFile: GradleDslFile by lazy {
    // This is in order to get a valid GradleDslFile object so that we can
    // create fake GradleDslElements. We initialize this lazily since we need
    // everything to be set up before we can call getGradleBuildModel().
    writeToBuildFile("")
    (gradleBuildModel as GradleBuildModelImpl).dslFile
  }

  protected fun GradleDslBlockModel.dslElement(): GradlePropertiesDslElement {
    val field = GradleDslBlockModel::class.java.getDeclaredField("myDslElement")
    field.isAccessible = true
    return field.get(this) as GradlePropertiesDslElement
  }

  /**
   * We use this method to copy strings to ensure that bugs to do with using "==" aren't present.
   */
  protected fun String.copy(): String {
    return "" + this
  }

  @Test
  fun testCopy() {
    val str = "Hello"
    assertFalse(str === str.copy())
    assertThat(str, equalTo(str.copy()))
  }

  protected fun createLiteral(name: String = "fake", parent: GradleDslElement = gradleDslFile): GradleDslLiteral {
    return GradleDslLiteral(parent, GradleNameElement.fake(name.copy()))
  }

  protected fun createMethodCall(methodName: String,
                                 statement: String = "unusedStatement",
                                 parent: GradleDslElement = gradleDslFile): GradleDslMethodCall {
    return GradleDslMethodCall(parent, GradleNameElement.create(statement.copy()), methodName.copy())
  }

  protected fun createExpressionMap(name: GradleNameElement = GradleNameElement.empty()): GradleDslExpressionMap {
    return GradleDslExpressionMap(gradleDslFile, name, false)
  }

  protected fun createClosure(parent: GradleDslElement): GradleDslClosure {
    return GradleDslClosure(parent, null, GradleNameElement.empty())
  }
}