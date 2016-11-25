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
    doTest(
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
    doTest(
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

fun doTest(before: String, expectedText: String, settings: CodeStyleSettings = CodeStyleSettings()) {
  var root = getRoot(before.trimStart())
  var beforeText = root.text

  val rightMargin = beforeText.indexOf('|')
  if (rightMargin > 0) {
    settings.setRightMargin(null, rightMargin)
    root = getRoot(before.trimStart().replace("|", ""))
    beforeText = root.text
  }
  
  val rootFormattingBlock = root.toFormattingBlock(0)

  val document = EditorFactory.getInstance().createDocument(beforeText)
  val model = TestFormattingModel(rootFormattingBlock, document)

  FormatterEx.getInstanceEx().format(model, settings, settings.indentOptions, null)

  TestCase.assertEquals(expectedText.trimStart(), document.text)
}