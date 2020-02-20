// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase


class JSSupportTest : GrazieTestBase() {
  fun `test spellcheck in constructs`() {
    runHighlightTestForFile("ide/language/js/Constructs.js")
  }

  fun `test grammar check in docs`() {
    runHighlightTestForFile("ide/language/js/Docs.js")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("ide/language/js/StringLiterals.js")
  }

  fun `test grammar check in jsx`() {
    runHighlightTestForFile("ide/language/js/Example.jsx")
  }
}
