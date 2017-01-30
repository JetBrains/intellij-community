/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert.assertEquals
import java.util.regex.Pattern


class InlayHintsChecker(private val myFixture: CodeInsightTestFixture) {

  private var isParamHintsEnabledBefore = false
  
  companion object {
    val pattern: Pattern = Pattern.compile("<hint\\s+text=\"([0-9a-zA-Z]+)\"/>")
    
    private val default = ParameterNameHintsSettings()
  }

  fun setUp() {
    val settings = EditorSettingsExternalizable.getInstance()
    isParamHintsEnabledBefore = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = true
  }

  fun tearDown() {
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = isParamHintsEnabledBefore
    val hintSettings = ParameterNameHintsSettings.getInstance()
    
    hintSettings.loadState(default.state)
    hintSettings.isShowForParamsWithSameType = default.isShowForParamsWithSameType
    hintSettings.isDoNotShowIfMethodNameContainsParameterName = default.isDoNotShowIfMethodNameContainsParameterName
  }
  
  fun checkInlays(fileName: String, text: String) {
    myFixture.configureByText(fileName, text)

    val file = myFixture.file
    val document = myFixture.getDocument(file)
    val expectedInlays = extractInlays(document)
    val actualInlays = getActualInlays()

    assertEquals(expectedInlays.size, actualInlays.size)
    
    actualInlays.zip(expectedInlays).forEach {
      assertEquals(it.second, it.first)
    }
  }

  private fun getActualInlays(): List<InlayInfo> {
    myFixture.doHighlighting()
    val editor = myFixture.editor
    val allInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
    
    val hintManager = ParameterHintsPresentationManager.getInstance()
    return allInlays
      .filter { hintManager.isParameterHint(it) }
      .map { InlayInfo(hintManager.getHintText(it), it.offset) }
      .sortedBy { it.offset }
  }

  fun extractInlays(document: Document): List<InlayInfo> {
    val text = document.text
    val matcher = pattern.matcher(text)

    val inlays = mutableListOf<InlayInfo>()
    var extractedLength = 0

    while (matcher.find()) {
      val start = matcher.start()
      val matchedLength = matcher.end() - start

      val realStartOffset = start - extractedLength
      inlays += InlayInfo(matcher.group(1), realStartOffset)

      removeText(document, realStartOffset, matchedLength)
      extractedLength += (matcher.end() - start)
    }

    return inlays
  }

  private fun removeText(document: Document, realStartOffset: Int, matchedLength: Int) {
    WriteCommandAction.runWriteCommandAction(myFixture.project, {
      document.replaceString(realStartOffset, realStartOffset + matchedLength, "")
    })
  }


}