// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil

internal class IdeDocumentHistoryFunctionalTest : HeavyFileEditorManagerTestCase() {
  fun testNavigateBetweenEditLocations() {
    myFixture.configureByText("${getTestName(false)}.txt", """
      <caret>line1



      line2



      line3""".trimIndent())
    myFixture.type(' ')
    moveCaret4LinesDown()
    myFixture.type(' ')
    moveCaret4LinesDown()

    myFixture.checkResult("""
       line1



      l ine2



      li<caret>ne3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
       <caret>line1



      l ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
  }

  fun testNavigateBetweenEditLocationsWithMultiCaret() {
    myFixture.configureByText("${getTestName(false)}.txt", """
      <caret>li<caret>ne1
      -------
      -------
      -------
      line2
      -------
      -------
      -------
      longer_line3""".trimIndent())
    myFixture.type("AAA")
    moveCaret4LinesDown()
    myFixture.type("BBB")
    moveCaret4LinesDown()

    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBBe2BBB
      -------
      -------
      -------
      longer<caret>_line<caret>3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
      AAA<caret>liAAA<caret>ne1
      -------
      -------
      -------
      linBBBe2BBB
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
  }

  fun testForwardToANearPlace() {
    myFixture.configureByText("${getTestName(false)}.java", """
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_DECLARATION)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_BACK)
    myFixture.checkResult("""
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_FORWARD)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
  }

  private fun moveCaret4LinesDown() {
    for (i in 0..3) {
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    }
  }
}
