// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.jsonpath.psi.JsonPathParserDefinition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.projectFixture

abstract class JsonPathParsingTestCase(subFolder: String) {
  companion object {
    private val projectFixture = projectFixture()
  }

  protected val parsing by parsingFixture(
    projectFixture,
    PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonpath/parser/" + subFolder,
    ".jsonpath",
    definitions = arrayOf(JsonPathParserDefinition()),
  )
}