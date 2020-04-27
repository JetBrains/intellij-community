// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl


class MarkdownSupportTest : GrazieTestBase() {
  override fun setUp() {
    super.setUp()
    StringUtil.getWordsIn("Sdf")
    // IDEA-228789 markdown change PSI/document/model during highlighting
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  }

  fun `test grammar check in file`() {
    runHighlightTestForFile("ide/language/markdown/Example.md")
  }
}
