/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting.engine

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.Block
import com.intellij.formatting.FormatterEx
import com.intellij.formatting.engine.testModel.TestFormattingModel
import com.intellij.formatting.engine.testModel.getRoot
import com.intellij.formatting.toFormattingBlock
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class FormatterEngineTests : LightPlatformTestCase() {

  @Test
  fun `test simple alignment`() {
    doReformatTest(
      """
[a0]fooooo [a1]foo
[a0]go [a1]boo
""",
      """
fooooo foo
go     boo
""")
  }

  @Test
  fun `test empty block alignment`() {
    doReformatTest(
      """
[a0]fooooo [a1]
[a0]go [a1]boo
""",
      """
fooooo 
go     boo
""")

  }

}

class TestData(val rootBlock: Block, val textToFormat: String, val markerPosition: Int?)

fun doReformatTest(before: String, expectedText: String, settings: CodeStyleSettings = CodeStyle.createTestSettings()) {
  val data = extractFormattingTestData(before)

  val rightMargin = data.markerPosition
  if (rightMargin != null) {
    settings.setRightMargin(null, rightMargin)
  }
  
  val document = EditorFactory.getInstance().createDocument(data.textToFormat)
  val model = TestFormattingModel(data.rootBlock, document)

  FormatterEx.getInstanceEx().format(model, settings, settings.indentOptions, null)

  TestCase.assertEquals(expectedText.trimStart(), document.text)
}

fun extractFormattingTestData(before: String) : TestData {
  var root = getRoot(before.trimStart())
  var beforeText = root.text

  val marker = beforeText.indexOf('|')
  if (marker > 0) {
    root = getRoot(before.trimStart().replace("|", ""))
    beforeText = root.text
  }

  val rootBlock = root.toFormattingBlock(0)
  return TestData(rootBlock, beforeText, if (marker > 0) marker else null)
}