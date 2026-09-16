// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.intellij.compose.ide.plugin.resources.ComposeResourcesCommonMainOnly
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val STRINGS_FILE_PATH = "composeApp/src/commonMain/composeResources/values/strings.xml"
private const val QUALIFIED_STRINGS_FILE_PATH = "composeApp/src/commonMain/composeResources/values-ro/strings.xml"
private const val DRAWABLE_FILE_PATH = "composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml"

@ComposeResourcesCommonMainOnly
class ComposeResourcesEmmetLikeCustomLiveTemplateTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test emmet-like template applies only in values xml files`() = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      try {
        assertTabExpansion(
          relativePath = STRINGS_FILE_PATH,
          before = xmlWithAbbreviation("resources"),
          after = """
            <resources>
                <string name="greeting">Hello</string>
                
            </resources>
          """
        )

        assertTabExpansion(
          relativePath = QUALIFIED_STRINGS_FILE_PATH,
          before = xmlWithAbbreviation("resources"),
          after = """
            <resources>
                <string name="greeting">Hello</string>
                
            </resources>
          """
        )

        assertTabExpansion(
          relativePath = DRAWABLE_FILE_PATH,
          before = xmlWithAbbreviation("vector"),
          after = """
            <vector>
              greeting{Hello}
            </vector>
          """
        )

        assertTabExpansion(
          relativePath = QUALIFIED_STRINGS_FILE_PATH,
          before = """
            <resources>
                <string name="greeting">Hello</string>$CARET_TAG
                
            </resources>
          """.trimIndent(),
          after = """
            <resources>
                <string name="greeting">Hello</string>
                
            </resources>
          """
        )
      }
      finally {
        revertUnsavedDocuments()
      }
    }
  }

  private fun assertTabExpansion(relativePath: String, before: String, after: String) {
    codeInsightFixture.configureFromExistingVirtualFile(canonicalProjectFile(relativePath))
    setEditorTextWithCaret(before)

    val actual = before.replace(CARET_TAG, "").trimIndent()
    val expected = after.trimIndent()
    if (actual == expected) {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
      val presentation = codeInsightFixture.testAction(action)
      assertFalse(presentation.isEnabledAndVisible, "Live template expansion action should not be available in this context")
    }
    else {
      codeInsightFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
      codeInsightFixture.checkResult(expected)
    }
  }

  private fun xmlWithAbbreviation(rootTagName: String): String =
    """
      <$rootTagName>
        greeting{Hello}$CARET_TAG
      </$rootTagName>
    """.trimIndent()

  private fun setEditorTextWithCaret(text: String) {
    val caretOffset = text.indexOf(CARET_TAG)
    assertTrue(caretOffset >= 0, "Test text should contain $CARET_TAG")

    WriteCommandAction.runWriteCommandAction(project) {
      codeInsightFixture.editor.document.setText(text.replace(CARET_TAG, ""))
      codeInsightFixture.editor.caretModel.moveToOffset(caretOffset)
    }
  }
}
