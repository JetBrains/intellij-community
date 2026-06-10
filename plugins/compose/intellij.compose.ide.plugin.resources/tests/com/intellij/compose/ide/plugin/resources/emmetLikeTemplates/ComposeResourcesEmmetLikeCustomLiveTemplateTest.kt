// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class ComposeResourcesEmmetLikeCustomLiveTemplateTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test emmet-like template applies only in values xml files`() {
    val files = importProjectFromTestData()
    timeoutRunBlocking(context = Dispatchers.EDT) {
      assertTabExpansion(
        file = files.findStringsXmlFile(),
        before = xmlWithAbbreviation("resources"),
        after = """
          <resources>
              <string name="greeting">Hello</string>
              
          </resources>
        """
      )

      assertTabExpansion(
        file = files.findQualifiedStringsXmlFile(),
        before = xmlWithAbbreviation("resources"),
        after = """
          <resources>
              <string name="greeting">Hello</string>
              
          </resources>
        """
      )

      assertTabExpansion(
        file = files.findComposeResourcesXmlFile(),
        before = xmlWithAbbreviation("vector"),
        after = """
          <vector>
            greeting{Hello}
          </vector>
        """
      )

      assertTabExpansion(
        file = files.findQualifiedStringsXmlFile(),
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
  }

  private fun assertTabExpansion(file: VirtualFile, before: String, after: String) {
    codeInsightTestFixture.openFileInEditor(file)
    setEditorTextWithCaret(before)

    val actual = before.replace(CARET_TAG, "").trimIndent()
    val expected = after.trimIndent()
    if (actual == expected) {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
      val presentation = codeInsightTestFixture.testAction(action)
      assertFalse("Live template expansion action should not be available in this context", presentation.isEnabledAndVisible)
    }
    else {
      codeInsightTestFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
      codeInsightTestFixture.checkResult(expected)
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
    assertTrue("Test text should contain $CARET_TAG", caretOffset >= 0)

    runWriteAction {
      codeInsightTestFixture.editor.document.setText(text.replace(CARET_TAG, ""))
      codeInsightTestFixture.editor.caretModel.moveToOffset(caretOffset)
    }
  }

  private fun List<VirtualFile>.findStringsXmlFile(): VirtualFile =
    first { it.path.endsWith("composeApp/src/commonMain/composeResources/values/strings.xml") }

  private fun List<VirtualFile>.findQualifiedStringsXmlFile(): VirtualFile =
    first { it.path.endsWith("composeApp/src/commonMain/composeResources/values-ro/strings.xml") }

  private fun List<VirtualFile>.findComposeResourcesXmlFile(): VirtualFile =
    first { it.path.endsWith("composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml") }
}