// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes

class XValuePresentationUtilTest : UsefulTestCase() {
  fun testRenderValues() {
    doTestValues("abc.\n\t\t\tcde", "abc.\\n\\t\\t\\tcde")
    doTestValues("abc\n\t\t\t.cde", "abc\\n\\t\\t\\t.cde")
    doTestValues("abc  .cde", "abc  .cde")
    doTestValues("abc;\n         cde", "abc;\\n         cde")
    doTestValues("abc;\ncde", "abc;\\ncde")
  }

  fun testRenderNames() {
    doTestNames("abcdef", "abcdef")
    doTestNames("abc.\n\t\t\tcde", "abc. cde")
    doTestNames("abc\n\t\t\t.cde", "abc .cde")
    doTestNames("abc  .cde", "abc .cde")
    doTestNames("abc    .    cde", "abc . cde")
    doTestNames("abc;\n         cde", "abc; cde")
    doTestNames("abc;\ncde", "abc; cde")
    doTestNames("abc; cde", "abc; cde")
    doTestNames("abc\n         ", "abc")
    doTestNames("abc\n         d", "abc d")
    doTestNames("            \nabc\n         ", "abc")
    doTestNames("abcdefghijklmnopqrstuvwxyz", "abcdefghijklmno", 15)
    doTestNames("abcdefghijklmn\n", "abcdefghijklmn", 15)
    doTestNames("abcdefghijklm\n ", "abcdefghijklm", 15)
    doTestNames("abcdefghijklm\no", "abcdefghijklm o", 15)
  }

  companion object {
    private fun doTestValues(before: String, after: String, maxOutputLength: Int = Int.MAX_VALUE) {
      val output: ColoredTextContainer = SimpleColoredText()
      XValuePresentationUtil.renderValue(before, output, SimpleTextAttributes.REGULAR_ATTRIBUTES, maxOutputLength, null, null)
      assertEquals(after, output.toString())
    }

    private fun doTestNames(before: String, after: String, maxOutputLength: Int = Int.MAX_VALUE) {
      val output = StringBuilder()
      XValuePresentationUtil.renderName(before, maxOutputLength) { s: String? -> output.append(s) }
      assertEquals(after, output.toString())
    }
  }
}
