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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.UNKNOWN
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.junit.Assume.assumeTrue
import org.junit.Test

class RawTextTest : GradleFileModelTestCase() {
  private fun set(setFunc: (GradlePropertyModel) -> Unit): ((GradlePropertyModel) -> Unit) -> Unit {
    return {
      writeToBuildFile("")

      val buildModel = gradleBuildModel
      val propertyModel = buildModel.ext().findProperty("newProp")
      setFunc.invoke(propertyModel)

      it.invoke(propertyModel)

      applyChangesAndReparse(buildModel)

      it.invoke(propertyModel)
    }
  }

  // This method is purely for naming purposes.
  private fun (((GradlePropertyModel) -> Unit) -> Unit).validate(func: (GradlePropertyModel) -> Unit) {
    this.invoke { func.invoke(it) }
  }

  @Test
  fun testSetLiteral() {
    set {
      it.setValue(RawText("25", "25"))
    }.validate {
      verifyPropertyModel(it, INTEGER_TYPE, 25, INTEGER, REGULAR, 0, "newProp")
    }
  }

  @Test
  fun textSetUnknownMethodCall() {
    val quotes = if(isGroovy) "'" else "\""
    set {
      it.setValue(RawText("getDefaultProguardFile('android.txt')", "getDefaultProguardFile(\"android.txt\")"))
    }.validate {
      verifyPropertyModel(it, STRING_TYPE, "getDefaultProguardFile(${quotes}android.txt${quotes})", UNKNOWN, REGULAR, 0, "newProp")
    }
  }

  @Test
  fun testSetUnknownExpression() {
    // In KTS we cannot use ** as this will result in a broken psi tree different from what we want to get.
    // This highlights that a RawText consumer will very likely want to use different text for KTS and Groovy.
    val expectedValueText = when {
      isGroovy -> "1 + (4 * 5)**2 - 7"
      else -> "1 + (4 * 5) * 2 - 7"
    }
    set {
      it.setValue(RawText("1 + (4 * 5)**2 - 7", "1 + (4 * 5) * 2 - 7"))
    }.validate {
      verifyPropertyModel(it, STRING_TYPE, expectedValueText, UNKNOWN, REGULAR, 0, "newProp")
    }
  }

  @Test
  fun testSetReference() {
    set {
      it.setValue(RawText("prop1", "prop1"))
    }.validate {
      verifyPropertyModel(it, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0, "newProp")
    }
  }

  @Test
  fun testSetIndexReference() {
    set {
      it.setValue(RawText("prop1[2]", "prop1[2]"))
    }.validate {
      verifyPropertyModel(it, STRING_TYPE, "prop1[2]", REFERENCE, REGULAR, 0, "newProp")
    }
  }
}