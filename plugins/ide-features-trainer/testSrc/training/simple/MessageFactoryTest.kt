// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.simple

import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.*
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import training.FeaturesTrainerIcons
import training.ui.LearningUiManager
import training.ui.MessageFactory

class MessageFactoryTest : BasePlatformTestCase() {
  fun testTextWithoutTags() {
    val parts: List<TextPart> = convertOneParagraph("Some text. Text, sss!")

    assertTrue(parts.size == 1)
    assertTrue(matchRegularText(parts[0], "Some text. Text, sss!"))
  }

  fun testStrong() {
    val parts: List<TextPart> = convertOneParagraph("Some text, aaa <strong>Strong text</strong> some other text.")

    assertTrue(parts.size == 3)
    assertTrue(matchRegularText(parts[0], "Some text, aaa "))
    assertTrue(matchRegularText(parts[1], "Strong text", expectedBold = true))
    assertTrue(matchRegularText(parts[2], " some other text."))
  }

  fun testCode() {
    val parts: List<TextPart> = convertOneParagraph("Type in the editor: <code>some code to write</code>")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], "Type in the editor: "))
    assertTrue(match<CodeTextPart>(parts[1], "some code to write"))
  }

  fun testShortcut() {
    val parts: List<TextPart> = convertOneParagraph("Press <shortcut>Ctrl+F</shortcut>.")

    assertTrue(parts.size == 3)
    assertTrue(matchRegularText(parts[0], "Press "))
    assertTrue(matchShortcut(parts[1], "Ctrl+F", expectedRaw = true))
    assertTrue(matchRegularText(parts[2], "."))
  }

  fun testRawShortcut() {
    val parts: List<TextPart> = convertOneParagraph("Press <raw_shortcut>pressed ENTER</raw_shortcut>")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], "Press "))
    assertTrue(matchShortcut(parts[1], "pressed ENTER", expectedRaw = true))
  }

  fun testAction() {
    val parts: List<TextPart> = convertOneParagraph("Try action <action>RecentFiles</action>")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], "Try action "))
    assertTrue(matchShortcut(parts[1], "RecentFiles", expectedRaw = false))
  }

  fun testIdeName() {
    val parts: List<TextPart> = convertOneParagraph("<ide/> is very powerful.")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], ApplicationNamesInfo.getInstance().fullProductName))
    assertTrue(matchRegularText(parts[1], " is very powerful."))
  }

  fun testWebLink() {
    val parts: List<TextPart> = convertOneParagraph("Go to <a href=\"some link\">this link</a>")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], "Go to "))
    assertTrue(match<LinkTextPart>(parts[1], "this link"))
  }

  fun testCallback() {
    val callbackId = LearningUiManager.addCallback { }
    val parts: List<TextPart> = convertOneParagraph("<callback id=\"${callbackId}\">configure</callback> interpreter.")

    assertTrue(parts.size == 2)
    assertTrue(match<LinkTextPart>(parts[0], "configure"))
    assertTrue(matchRegularText(parts[1], " interpreter."))
  }

  fun testIconIdx() {
    val icon = FeaturesTrainerIcons.PluginIcon
    val index = LearningUiManager.getIconIndex(icon)
    val parts: List<TextPart> = convertOneParagraph("Press <icon_idx>$index</icon_idx>")

    assertTrue(parts.size == 2)
    assertTrue(matchRegularText(parts[0], "Press "))
    assertTrue(parts[1].let { it is IconTextPart && it.icon == icon })
  }

  fun testIllustration() {
    val icon = FeaturesTrainerIcons.PluginIcon
    val index = LearningUiManager.getIconIndex(icon)
    val parts: List<TextPart> = convertOneParagraph("<illustration>$index</illustration>")

    assertTrue(parts.size == 1)
    assertTrue(parts[0].let { it is IllustrationTextPart && it.icon == icon })
  }

  fun testNonBreakSpaces() {
    val parts: List<TextPart> = convertOneParagraph("<action>EditorCopy</action> .")

    assertTrue(parts.size == 2)
    assertTrue(matchShortcut(parts[0], "EditorCopy", expectedRaw = false))
    assertTrue(matchRegularText(parts[1], "\u00A0" + "."))
  }

  fun testTwoParagraphText() {
    val paragraphs = convert("For example, press <action>GotoAction</action> to open <strong>Search for Action</strong> popup.\n" +
                             "Now, please type <code>some string</code> and press <raw_shortcut>pressed ENTER</raw_shortcut>.")
    assertTrue(paragraphs.size == 2)

    val firstParts = paragraphs[0].textParts
    assertTrue(firstParts.size == 5)
    assertTrue(matchRegularText(firstParts[0], "For example, press "))
    assertTrue(matchShortcut(firstParts[1], "GotoAction", expectedRaw = false))
    assertTrue(matchRegularText(firstParts[2], " to open "))
    assertTrue(matchRegularText(firstParts[3], "Search for Action", expectedBold = true))
    assertTrue(matchRegularText(firstParts[4], " popup."))

    val secondParts = paragraphs[1].textParts
    assertTrue(matchRegularText(secondParts[0], "Now, please type "))
    assertTrue(match<CodeTextPart>(secondParts[1], "some string"))
    assertTrue(matchRegularText(secondParts[2], " and press "))
    assertTrue(matchShortcut(secondParts[3], "pressed ENTER", expectedRaw = true))
    assertTrue(matchRegularText(secondParts[4], "."))
  }

  private fun convertOneParagraph(text: String): List<TextPart> {
    val paragraphs: List<TextParagraph> = convert(text)
    assertTrue(paragraphs.size == 1)
    return paragraphs.single().textParts
  }

  private fun convert(text: String): List<TextParagraph> {
    return MessageFactory.convert(text)
  }

  private inline fun <reified T : TextPart> match(textPart: TextPart, expectedText: String): Boolean {
    return textPart is T && textPart.text == expectedText
  }

  private fun matchRegularText(textPart: TextPart, expectedText: String, expectedBold: Boolean = false): Boolean {
    return textPart is RegularTextPart && textPart.text == expectedText && textPart.isBold == expectedBold
  }

  private fun matchShortcut(textPart: TextPart, expectedText: String, expectedRaw: Boolean): Boolean {
    return textPart is ShortcutTextPart && textPart.text == expectedText && textPart.isRaw == expectedRaw
  }
}