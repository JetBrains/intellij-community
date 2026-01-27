// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ConsentSettingsBodyTest {

  @Test
  fun `panel for JetBrains vendor contains comment with company name and group name`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent"))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val texts = findAllTextContent(ui)
    val hasJetBrainsComment = texts.any { text ->
      text.contains("JetBrains") && text.contains("Help shape")
    }
    val hasJetBrainsGroupName = texts.any { text ->
      text.contains("Applied to All Installed")
    }
    assertTrue(hasJetBrainsComment, "JetBrains vendor panel should contain introductory comment")
    assertTrue(hasJetBrainsGroupName, "JetBrains vendor panel should contain general consents group name")
  }

  @Test
  fun `panel for non-JetBrains vendor does not contain JetBrains-specific comment and group name`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent"))
    val ui = ConsentSettingsUi(true, false)
    ui.reset(consents)

    val texts = findAllTextContent(ui)
    val hasJetBrainsComment = texts.any { text ->
      text.contains("JetBrains") && text.contains("Help shape")
    }
    val hasJetBrainsGroupName = texts.any { text ->
      text.contains("Applied to All Installed")
    }
    assertFalse(hasJetBrainsComment, "Non-JetBrains vendor panel should not contain JetBrains-specific comment")
    assertFalse(hasJetBrainsGroupName, "Non-JetBrains vendor panel should not contain general consents group name")
  }

  @Test
  fun `panel creates checkboxes for multiple consents`() {
    val consents = listOf(
      createTestConsent("test.consent.1", "Test Consent 1"),
      createTestConsent("test.consent.2", "Test Consent 2")
    )
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    assertTrue(checkboxes.isNotEmpty(), "Panel should contain checkboxes for consents")
  }

  @Test
  fun `panel in preferences mode adds checkboxes for single consent`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent"))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    assertTrue(checkboxes.isNotEmpty(), "Preferences mode should add checkbox even for single consent")
  }

  @Test
  fun `panel shows empty message when no consents`() {
    val ui = ConsentSettingsUi(true, true)
    ui.reset(emptyList())

    val hasEmptyMessage = findAllTextContent(ui).any { text ->
      text.contains("no data-sharing options") || text.contains("no data sharing options")
    }
    assertTrue(hasEmptyMessage, "Panel should contain 'no data sharing options' message")
  }

  @Test
  fun `panel checkbox selection reflects consent accepted state`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent", isAccepted = true))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    assertEquals(1, checkboxes.size)
    assertTrue(checkboxes.first().isSelected, "Checkbox should be selected for accepted consent")
  }

  @Test
  fun `panel checkbox not selected for non-accepted consent`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent", isAccepted = false))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    assertEquals(1, checkboxes.size)
    assertFalse(checkboxes.first().isSelected, "Checkbox should not be selected for non-accepted consent")
  }

  @Test
  fun `isModified returns false when consents unchanged`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent", isAccepted = false))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    assertFalse(ui.isModified(consents), "isModified should return false when consents unchanged")
  }

  @Test
  fun `isModified returns true when checkbox toggled`() {
    val consents = listOf(createTestConsent("test.consent.1", "Test Consent", isAccepted = false))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    checkboxes.first().isSelected = true

    assertTrue(ui.isModified(consents), "isModified should return true when checkbox toggled")
  }

  @Test
  fun `apply updates consent state`() {
    val consents = mutableListOf(createTestConsent("test.consent.1", "Test Consent", isAccepted = false))
    val ui = ConsentSettingsUi(true, true)
    ui.reset(consents)

    val checkboxes = UIUtil.findComponentsOfType(ui, JCheckBox::class.java)
    checkboxes.first().isSelected = true

    ui.apply(consents)

    assertEquals(1, consents.size)
    assertTrue(consents.first().isAccepted, "Consent should be accepted after apply")
  }

  private fun createTestConsent(id: String, name: String, isAccepted: Boolean = false): Consent {
    return Consent(id, Version.UNKNOWN, name, "Test consent text for $name", isAccepted, false, "en")
  }

  /**
   * Finds all text content from JLabels and JTextComponents (including JEditorPane used by comment()).
   * Strips HTML tags to get plain text for comparison.
   */
  private fun findAllTextContent(container: JComponent): List<String> {
    val result = mutableListOf<String>()

    UIUtil.findComponentsOfType(container, JLabel::class.java).forEach { label ->
      label.text?.let { result.add(stripHtml(it)) }
    }

    // used by comment()
    UIUtil.findComponentsOfType(container, JTextComponent::class.java).forEach { textComponent ->
      textComponent.text?.let { result.add(stripHtml(it)) }
    }

    return result
  }

  private fun stripHtml(text: String): String {
    return text.replace(Regex("<[^>]*>"), "")
  }
}
