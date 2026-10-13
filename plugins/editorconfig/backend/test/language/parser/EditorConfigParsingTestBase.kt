// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.editorconfig.common.plugin.EditorConfigParserDefinition
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.fixture.projectFixture

abstract class EditorConfigParsingTestBase(dataPath: String) {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val parsing by parsingFixture(
    projectFixture,
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/parser/$dataPath",
    "editorconfig",
    true,
    EditorConfigParserDefinition(),
  )

  protected fun doTest() = parsing.doTest(true)
}
