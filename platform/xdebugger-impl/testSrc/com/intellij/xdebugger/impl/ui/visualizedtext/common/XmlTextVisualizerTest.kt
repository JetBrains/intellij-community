// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.idea.TestFor

class XmlTextVisualizerTest : FormattedTextVisualizerTestCase(XmlTextVisualizer()) {

  fun testSomeValidXml() {
    checkPositive(
      """<note><to>Tove</to><from>Jani</from><heading type="reminder">Reminder</heading><body priority="high">Don't forget our meeting tomorrow!</body></note>""",
      """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <note>
            <to>Tove</to>
            <from>Jani</from>
            <heading type="reminder">Reminder</heading>
            <body priority="high">Don't forget our meeting tomorrow!</body>
        </note>

      """.trimIndent())
  }

  fun testLFvsCRLF() {
    val input =
      "<foo>\n<bar>Hello</bar></foo>"
    val output = """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <foo>
            
            <bar>Hello</bar>
        </foo>
        
      """.trimIndent()

    checkPositive(input, output)
    checkPositive(input.replace("\n", "\r\n"), output)
  }

  fun testNotSoValidXml() {
    checkNegative("<p>Hello, <b>world</b>!")
  }

  fun testNotXml() {
    checkNegative("Hello, world!")
  }

  fun testNotStandaloneXml() {
    checkNegative("Hello, <b>world</b>!")
  }

  @TestFor(issues = ["EA-1461644"])
  fun testWithoutNamespaceForPrefix() {
    checkNegative(
      """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <spml:modifyRequest>   
          <modification>
            <valueObject xsi:type="halo">
            </valueObject>   
          </modification>
        </spml:modifyRequest>
      """.trimIndent())
  }
}