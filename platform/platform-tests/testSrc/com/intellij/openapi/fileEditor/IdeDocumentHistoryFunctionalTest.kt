// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil

internal class IdeDocumentHistoryFunctionalTest : HeavyFileEditorManagerTestCase() {
  fun testNavigateBetweenEditLocations() {
    myFixture.configureByText("${getTestName(false)}.txt",
                              """<caret>line1



line2



line3""")
    myFixture.type(' ')
    moveCaret4LinesDown()
    myFixture.type(' ')
    moveCaret4LinesDown()

    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult(""" line1



l <caret>ine2



line3""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult(""" <caret>line1



l ine2



line3""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult(""" line1



l <caret>ine2



line3""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult(""" line1



l <caret>ine2



line3""")
  }

  fun testForwardToANearPlace() {
    myFixture.configureByText(getTestName(false) + ".java",
                              """class AA {}

class BV extends A<caret>A {}""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_DECLARATION)
    myFixture.checkResult("""class <caret>AA {}

class BV extends AA {}""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_BACK)
    myFixture.checkResult("""class AA {}

class BV extends A<caret>A {}""")
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_FORWARD)
    myFixture.checkResult("""class <caret>AA {}

class BV extends AA {}""")
  }

  private fun moveCaret4LinesDown() {
    for (i in 0..3) {
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    }
  }
}
