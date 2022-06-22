// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.simple

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import training.ui.LearningUiManager
import training.ui.MessageFactory
import training.ui.MessagePart
import training.ui.MessagePart.MessageType.*

class MessageFactoryTest : BasePlatformTestCase() {
  fun testTextWithoutTags() {
    val parts: List<MessagePart> = convert("Some text. Text, sss!")

    assertTrue(parts.size == 1)
    assertTrue(parts[0].match("Some text. Text, sss!", TEXT_REGULAR))
  }

  fun testStrong() {
    val parts: List<MessagePart> = convert("Some text, aaa <strong>Strong text</strong> some other text.")

    assertTrue(parts.size == 3)
    assertTrue(parts[0].match("Some text, aaa ", TEXT_REGULAR))
    assertTrue(parts[1].match("Strong text", TEXT_BOLD))
    assertTrue(parts[2].match(" some other text.", TEXT_REGULAR))
  }

  fun testCode() {
    val parts: List<MessagePart> = convert("Type in the editor: <code>some code to write</code>")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("Type in the editor: ", TEXT_REGULAR))
    assertTrue(parts[1].match("some code to write", CODE))
  }

  fun testShortcut() {
    val parts: List<MessagePart> = convert("Press <shortcut>Ctrl+F</shortcut>.")

    assertTrue(parts.size == 3)
    assertTrue(parts[0].match("Press ", TEXT_REGULAR))
    assertTrue(parts[1].match("Ctrl+F", SHORTCUT))
    assertTrue(parts[2].match(".", TEXT_REGULAR))
  }

  fun testRawShortcut() {
    val parts: List<MessagePart> = convert("Press <raw_shortcut>pressed ENTER</raw_shortcut>")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("Press ", TEXT_REGULAR))
    assertTrue(parts[1].type == SHORTCUT)
  }

  fun testAction() {
    val parts: List<MessagePart> = convert("Try action <action>RecentFiles</action>")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("Try action ", TEXT_REGULAR))
    assertTrue(parts[1].type == SHORTCUT)
  }

  fun testIdeName() {
    val parts: List<MessagePart> = convert("<ide/> is very powerful.")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match(ApplicationNamesInfo.getInstance().fullProductName, TEXT_REGULAR))
    assertTrue(parts[1].match(" is very powerful.", TEXT_REGULAR))
  }

  fun testWebLink() {
    val parts: List<MessagePart> = convert("Go to <a href=\"some link\">this link</a>")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("Go to ", TEXT_REGULAR))
    assertTrue(parts[1].match("this link", LINK) && parts[1].link == "some link")
  }

  fun testCallback() {
    val callbackId = LearningUiManager.addCallback { }
    val parts: List<MessagePart> = convert("<callback id=\"${callbackId}\">configure</callback> interpreter.")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("configure", LINK) && parts[0].runnable != null)
    assertTrue(parts[1].match(" interpreter.", TEXT_REGULAR))
  }

  fun testIconIdx() {
    val parts: List<MessagePart> = convert("Press <icon_idx>1</icon_idx>")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].match("Press ", TEXT_REGULAR))
    assertTrue(parts[1].match("1", ICON_IDX))
  }

  fun testIllustration() {
    val parts: List<MessagePart> = convert("<illustration>3</illustration>")

    assertTrue(parts.size == 1)
    assertTrue(parts[0].match("3", ILLUSTRATION))
  }

  fun testNonBreakSpaces() {
    val parts: List<MessagePart> = convert("<action>EditorCopy</action> .")

    assertTrue(parts.size == 2)
    assertTrue(parts[0].type == SHORTCUT)
    assertTrue(parts[1].match("\u00A0" + ".", TEXT_REGULAR))
  }

  fun testTwoParagraphText() {
    val parts: List<MessagePart> = convert(
      "For example, press <action>GotoAction</action> to open <strong>Search for Action</strong> popup.\n" +
      "Now, please type <code>some string</code> and press <raw_shortcut>pressed ENTER</raw_shortcut>.")

    assertTrue(parts.size == 11)
    assertTrue(parts[0].match("For example, press ", TEXT_REGULAR))
    assertTrue(parts[1].type == SHORTCUT)
    assertTrue(parts[2].match(" to open ", TEXT_REGULAR))
    assertTrue(parts[3].match("Search for Action", TEXT_BOLD))
    assertTrue(parts[4].match(" popup.", TEXT_REGULAR))
    assertTrue(parts[5].match("\n", LINE_BREAK))
    assertTrue(parts[6].match("Now, please type ", TEXT_REGULAR))
    assertTrue(parts[7].match("some string", CODE))
    assertTrue(parts[8].match(" and press ", TEXT_REGULAR))
    assertTrue(parts[9].type == SHORTCUT)
    assertTrue(parts[10].match(".", TEXT_REGULAR))
  }

  private fun convert(text: String): List<MessagePart> {
    return MessageFactory.convert(text).onEach(MessagePart::updateTextAndSplit)
  }

  private fun MessagePart.match(expectedText: String, expectedType: MessagePart.MessageType): Boolean {
    return text == expectedText && type == expectedType
  }
}