// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.idea.TestFor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
class XmlTextVisualizerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val checker = TextVisualizerChecker(XmlTextVisualizer(), projectFixture)

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun testSomeValidXml(): Unit = timeoutRunBlocking {
    checker.checkPositive(
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

  @Test
  fun testLFvsCRLF(): Unit = timeoutRunBlocking {
    val input =
      "<foo>\n<bar>Hello</bar></foo>"
    val output = """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <foo>
            
            <bar>Hello</bar>
        </foo>
        
      """.trimIndent()

    checker.checkPositive(input, output)
    checker.checkPositive(input.replace("\n", "\r\n"), output)
  }

  @Test
  fun testNotSoValidXml() {
    checker.checkNegative("<p>Hello, <b>world</b>!")
  }

  @Test
  fun testNotXml() {
    checker.checkNegative("Hello, world!")
  }

  @Test
  fun testNotStandaloneXml() {
    checker.checkNegative("Hello, <b>world</b>!")
  }

  @TestFor(issues = ["EA-1461644"])
  @Test
  fun testWithoutNamespaceForPrefix() {
    checker.checkNegative(
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

  @TestFor(issues = ["GO-18010"])
  @Test
  fun testXXEVulnerabilityPrevention(): Unit = timeoutRunBlocking {
    val secretFile = tempDir.resolve("secret")
    secretFile.writeText("some secret password")
    val secretPath = secretFile.toAbsolutePath().toString()
    checker.checkPositive(
      """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <!DOCTYPE imp [
          <!ENTITY file SYSTEM "file://$secretPath" >
        ]><root>
        <poc>&file;</poc>
        </root>
      """.trimIndent(),
      """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <root>
            
            <poc/>
            
        </root>
        
      """.trimIndent(),
    )
  }
}