// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Soft wraps are on by default in the main editor, and the default masks select Markdown and other text files.
 * The defaults are not persisted: only an explicit user choice reaches `editor.xml`, so a future default applies
 * to every user who did not change the setting.
 * The test JVM runs in unit-test mode, where the flag default stays off and the editor tests stay deterministic.
 * The production default is asserted through [EditorSettingsExternalizable.defaultSoftWrapPlaces].
 */
internal class EditorSettingsSoftWrapDefaultsTest {
  private val defaultMasks = "*.md; *.txt; *.rst; *.adoc"

  @Test
  fun `the production default turns soft wraps on in the main editor only`() {
    assertThat(EditorSettingsExternalizable.defaultSoftWrapPlaces(false))
      .containsExactly(SoftWrapAppliancePlaces.MAIN_EDITOR)
  }

  @Test
  fun `the unit-test default keeps soft wraps off`() {
    assertThat(EditorSettingsExternalizable.defaultSoftWrapPlaces(true)).isEmpty()

    val settings = fresh()
    assertThat(settings.isUseSoftWraps).isFalse()
    assertThat(settings.softWrapFileMasks).isEqualTo(defaultMasks)
  }

  @Test
  fun `the defaults are not persisted`() {
    val saved = save(fresh())

    assertThat(saved).doesNotContain("USE_SOFT_WRAPS")
    assertThat(saved).doesNotContain("SOFT_WRAP_FILE_MASKS")
  }

  @Test
  fun `an empty stored value keeps soft wraps off`() {
    val settings = load("""<state><option name="USE_SOFT_WRAPS" value="" /></state>""")

    assertThat(settings.isUseSoftWraps).isFalse()
    assertThat(settings.softWrapFileMasks).isEqualTo(defaultMasks)
  }

  @Test
  fun `an explicit off choice stores the flag and nothing else`() {
    val settings = fresh()
    settings.isUseSoftWraps = true
    settings.isUseSoftWraps = false

    val saved = save(settings)
    assertThat(saved).contains("USE_SOFT_WRAPS")
    assertThat(saved).doesNotContain("SOFT_WRAP_FILE_MASKS")

    val reloaded = load(saved)
    assertThat(reloaded.state.USE_SOFT_WRAPS).isEmpty()
    assertThat(reloaded.isUseSoftWraps).isFalse()
  }

  @Test
  fun `a return to the default state leaves the config pristine`() {
    val settings = fresh()
    settings.isUseSoftWraps = false
    settings.isUseSoftWraps = true

    assertThat(settings.isUseSoftWraps).isTrue()
    val saved = save(settings)
    assertThat(saved).doesNotContain("USE_SOFT_WRAPS")
    assertThat(saved).doesNotContain("SOFT_WRAP_FILE_MASKS")
  }

  @Test
  fun `a stored value equal to the default heals to the unstored default on load`() {
    val settings = load("""<state><option name="USE_SOFT_WRAPS" value="MAIN_EDITOR" /></state>""")

    assertThat(settings.isUseSoftWraps).isTrue()
    assertThat(settings.state.USE_SOFT_WRAPS).isNull()
    assertThat(save(settings)).doesNotContain("USE_SOFT_WRAPS")
  }

  @Test
  fun `a toggle in another place does not touch the masks`() {
    val settings = fresh()

    settings.setUseSoftWraps(true, SoftWrapAppliancePlaces.CONSOLE)

    assertThat(settings.softWrapFileMasks).isEqualTo(defaultMasks)
    assertThat(save(settings)).doesNotContain("SOFT_WRAP_FILE_MASKS")
  }

  @Test
  fun `stored masks win over the default`() {
    val settings = load(
      """<state><option name="USE_SOFT_WRAPS" value="MAIN_EDITOR" /><option name="SOFT_WRAP_FILE_MASKS" value="*.java" /></state>""")

    assertThat(settings.softWrapFileMasks).isEqualTo("*.java")
  }

  @Test
  fun `a legacy enablement without stored masks gets the default masks`() {
    val settings = load("""<state><option name="USE_SOFT_WRAPS" value="MAIN_EDITOR" /></state>""")

    assertThat(settings.isUseSoftWraps).isTrue()
    assertThat(settings.softWrapFileMasks).isEqualTo(defaultMasks)
  }

  @Test
  fun `masks equal to the default are not persisted`() {
    val settings = fresh()
    settings.softWrapFileMasks = defaultMasks

    assertThat(save(settings)).doesNotContain("SOFT_WRAP_FILE_MASKS")
  }

  private fun fresh(): EditorSettingsExternalizable =
    EditorSettingsExternalizable(EditorSettingsExternalizable.OsSpecificState()).also { it.noStateLoaded() }

  private fun save(settings: EditorSettingsExternalizable): String =
    JDOMUtil.write(XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter()))

  private fun load(xml: String): EditorSettingsExternalizable {
    val options = XmlSerializer.deserialize(JDOMUtil.load(xml), EditorSettingsExternalizable.OptionSet::class.java)
    return EditorSettingsExternalizable(EditorSettingsExternalizable.OsSpecificState()).also { it.loadState(options) }
  }
}
