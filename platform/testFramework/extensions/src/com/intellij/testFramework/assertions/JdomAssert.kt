/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.assertions

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.util.io.readText
import com.intellij.util.isEmpty
import com.intellij.util.loadElement
import org.assertj.core.api.AbstractAssert
import org.assertj.core.internal.Objects
import org.jdom.Element
import java.io.File
import java.nio.file.Path

class JdomAssert(actual: Element?) : AbstractAssert<JdomAssert, Element?>(actual, JdomAssert::class.java) {
  fun isEmpty(): JdomAssert {
    isNotNull

    if (!actual.isEmpty()) {
      failWithMessage("Expected to be empty but was\n${JDOMUtil.writeElement(actual!!)}")
    }

    return this
  }

  @Deprecated("isEqualTo(file: Path)", ReplaceWith("isEqualTo(file.toPath())"))
  fun isEqualTo(file: File) = isEqualTo(file.toPath())

  fun isEqualTo(file: Path): JdomAssert {
    isNotNull

    val expected = loadElement(file)
    if (!JDOMUtil.areElementsEqual(actual, expected)) {
      throw FileComparisonFailure(null, StringUtilRt.convertLineSeparators(file.readText()), JDOMUtil.writeElement(actual!!), file.toString())
    }
    return this
  }

  fun isEqualTo(element: Element): JdomAssert {
    isNotNull

    if (!JDOMUtil.areElementsEqual(actual, element)) {
      isEqualTo(JDOMUtil.writeElement(element))
    }
    return this
  }

  fun isEqualTo(expected: String): JdomAssert {
    isNotNull

    Objects.instance().assertEqual(
      info,
      JDOMUtil.writeElement(actual!!),
      expected.trimIndent().removePrefix("""<?xml version="1.0" encoding="UTF-8"?>""").trimStart())

    return this
  }
}