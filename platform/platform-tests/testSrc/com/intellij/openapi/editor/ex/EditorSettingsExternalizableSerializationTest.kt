// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A corrupted `editor.xml` must never break editor creation.
 *
 * `EditorSettingsState` reads these settings eagerly in its constructor, which runs inside
 * `SettingsImpl.<init>` -> `EditorImpl.<init>`, so a `null` here means no editor can be opened at all.
 * xmlb writes `null` into an enum field when the stored `<option>` has no `value` attribute
 * (converter path, see `TagBinding`) or carries an unknown constant name (plain enum path,
 * see `ClassUtil.stringToEnum`).
 */
internal class EditorSettingsExternalizableSerializationTest {
  @Test
  fun `valueless CARET_EASING option falls back to the default`() {
    val settings = load("""<state><option name="CARET_EASING" /></state>""")

    assertThat(settings.caretEasing).isEqualTo(EditorSettings.CaretEasing.SNAPPY)
  }

  @Test
  fun `valueless LINE_NUMERATION option falls back to the default`() {
    val settings = load("""<state><option name="LINE_NUMERATION" /></state>""")

    assertThat(settings.lineNumeration).isEqualTo(EditorSettings.LineNumerationType.ABSOLUTE)
  }

  @Test
  fun `unknown LINE_NUMERATION constant falls back to the default`() {
    val settings = load("""<state><option name="LINE_NUMERATION" value="INVERTED" /></state>""")

    assertThat(settings.lineNumeration).isEqualTo(EditorSettings.LineNumerationType.ABSOLUTE)
  }

  @Test
  fun `unknown CARET_EASING constant falls back to the default`() {
    val settings = load("""<state><option name="CARET_EASING" value="BOUNCY" /></state>""")

    assertThat(settings.caretEasing).isEqualTo(EditorSettings.CaretEasing.SNAPPY)
  }

  @Test
  fun `legacy CARET_EASING names are still converted`() {
    assertThat(load("""<state><option name="CARET_EASING" value="NINJA" /></state>""").caretEasing)
      .isEqualTo(EditorSettings.CaretEasing.SNAPPY)
    assertThat(load("""<state><option name="CARET_EASING" value="EASE" /></state>""").caretEasing)
      .isEqualTo(EditorSettings.CaretEasing.GLIDING)
  }

  @Test
  fun `known values are preserved`() {
    assertThat(load("""<state><option name="CARET_EASING" value="GLIDING" /></state>""").caretEasing)
      .isEqualTo(EditorSettings.CaretEasing.GLIDING)
    assertThat(load("""<state><option name="LINE_NUMERATION" value="RELATIVE" /></state>""").lineNumeration)
      .isEqualTo(EditorSettings.LineNumerationType.RELATIVE)
  }

  @Test
  fun `a broken option is not written back`() {
    val settings = load("""<state><option name="CARET_EASING" /><option name="LINE_NUMERATION" /></state>""")

    val saved = JDOMUtil.write(XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter()))

    assertThat(saved).doesNotContain("CARET_EASING")
    assertThat(saved).doesNotContain("LINE_NUMERATION")
  }

  @Test
  fun `setting null falls back to the default instead of persisting null`() {
    val settings = EditorSettingsExternalizable(EditorSettingsExternalizable.OsSpecificState())

    settings.setCaretEasing(null)
    settings.setLineNumeration(null)

    assertThat(settings.caretEasing).isEqualTo(EditorSettings.CaretEasing.SNAPPY)
    assertThat(settings.lineNumeration).isEqualTo(EditorSettings.LineNumerationType.ABSOLUTE)
  }

  private fun load(xml: String): EditorSettingsExternalizable {
    val options = XmlSerializer.deserialize(JDOMUtil.load(xml), EditorSettingsExternalizable.OptionSet::class.java)
    return EditorSettingsExternalizable(EditorSettingsExternalizable.OsSpecificState()).also { it.loadState(options) }
  }
}
