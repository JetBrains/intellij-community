// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeStyle

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.LightPlatformTestCase

val xmlContent = """<root>
<body>
<arm>
<hand/>
</arm>
<arm></arm>
<leg></leg>
<leg>
<foot/>
</leg>
</body>
</root>"""

val lineIndents = listOf(
  "",
  "    ",
  "        ",
  "            ",
  "        ",
  "        ",
  "        ",
  "        ",
  "            ",
  "        ",
  "    ",
  ""
)

class GetLineIndentsTest : LightPlatformTestCase() {

  fun testGetLineIndentsEmpty() {
    val codeStyleManager = CodeStyleManager.getInstance(project)
    val file = createFile("a.xml", "")
    assertEquals(emptyList<String>(), codeStyleManager.getLineIndents(file))
  }

  fun testGetLineIndentsXml() {
    val codeStyleManager = CodeStyleManager.getInstance(project)
    val file = createFile("a.xml", xmlContent)
    assertEquals(lineIndents.toVisual(), codeStyleManager.getLineIndents(file)?.toVisual())
  }

  private fun Iterable<String>.toVisual() =
    joinToString("\n") { "*".repeat(it.length) }

}
