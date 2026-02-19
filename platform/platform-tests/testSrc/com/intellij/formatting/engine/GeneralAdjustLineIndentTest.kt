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
import com.intellij.formatting.FormatterEx
import com.intellij.formatting.engine.testModel.TestFormattingModel
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

/**
 * Migrated from GeneralCodeFormatterTest.java
 */
class GeneralAdjustLineIndentTest : LightPlatformTestCase() {

  @Test
  fun `test adjust line indent`() {
    val before = "[i_norm incomplete]a\n"
    val expectedText = "a\n    "

    val data = extractFormattingTestData(before)

    val document = EditorFactory.getInstance().createDocument(data.textToFormat)
    val model = TestFormattingModel(data.rootBlock.subBlocks[0], document)

    val settings = CodeStyle.createTestSettings()
    val textRange = TextRange(0, document.textLength)

    FormatterEx.getInstanceEx().adjustLineIndent(model, settings, settings.indentOptions, document.textLength - 1, textRange)

    TestCase.assertEquals(expectedText.trimStart(), document.text)
  }

}