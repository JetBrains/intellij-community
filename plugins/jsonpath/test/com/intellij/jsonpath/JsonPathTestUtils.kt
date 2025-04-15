// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.jsonpath.psi.JsonPathParserDefinition
import com.intellij.testFramework.ParsingTestCase
import com.intellij.testFramework.PlatformTestUtil

abstract class JsonPathParsingTestCase(subFolder: String) : ParsingTestCase(subFolder, ".jsonpath", JsonPathParserDefinition()) {
  override fun getTestDataPath(): String {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonpath/parser"
  }
}