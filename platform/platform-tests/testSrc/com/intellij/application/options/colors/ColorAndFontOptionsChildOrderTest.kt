// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

/**
 * The child order of the "Colors and Fonts" node is user visible, and a shipped IDE holds about 50 children.
 * That is too many for a reviewer to check by eye.
 *
 * The bean migration moves the sort from [DisplayPrioritySortable.compare] on the page instance to
 * `Weighted.COMPARATOR` on the declaration, so the order must be pinned before the first change.
 * This test is the snapshot. It must keep passing after every step of the migration, and a step that changes
 * the order must state why in its commit message.
 *
 * Two halves. The rule half masks the extension point with a fixed page set, so it states the sort rule and
 * it holds in every product. The snapshot half reads the pages that this test module really registers.
 */
@TestApplication
internal class ColorAndFontOptionsChildOrderTest {

  @TestDisposable
  private lateinit var disposable: Disposable

  /** A band goes before every other rule, and the bands follow the order of the [DisplayPriority] values. */
  @Test
  fun `the sort orders by band`() {
    maskPages(
      page("other", "A other", DisplayPriority.OTHER_SETTINGS),
      page("language", "B language", DisplayPriority.LANGUAGE_SETTINGS),
      page("general", "Z general", DisplayPriority.GENERAL_SETTINGS),
      page("key-language", "Key language", DisplayPriority.KEY_LANGUAGE_SETTINGS),
      page("code", "Code", DisplayPriority.CODE_SETTINGS),
      page("common", "Common", DisplayPriority.COMMON_SETTINGS),
    )
    maskPanelFactories()

    assertThat(childIds()).containsExactly(
      childId("general"),
      // the two hand-made font children hold the FONT_SETTINGS band, and they sort by name
      childId("ColorSchemeFont"),
      childId("ConsoleFont"),
      childId("common"),
      childId("code"),
      childId("key-language"),
      childId("language"),
      childId("other"),
    )
  }

  /** Inside one band the bigger weight goes first, and it beats the name. */
  @Test
  fun `a bigger weight goes before a name inside one band`() {
    maskPages(
      page("light", "A light page", DisplayPriority.LANGUAGE_SETTINGS),
      page("heavy", "Z heavy page", DisplayPriority.LANGUAGE_SETTINGS, weight = 10),
    )
    maskPanelFactories()

    assertThat(childIds()).containsSubsequence(childId("heavy"), childId("light"))
  }

  /** Two pages of one band and one weight sort by the name, and the case does not count. */
  @Test
  fun `a name is compared without the case`() {
    maskPages(
      page("upper", "B upper name", DisplayPriority.LANGUAGE_SETTINGS),
      page("lower", "a lower name", DisplayPriority.LANGUAGE_SETTINGS),
    )
    maskPanelFactories()

    assertThat(childIds()).containsSubsequence(childId("lower"), childId("upper"))
  }

  /** A page that states no priority lands in [DisplayPriority.LANGUAGE_SETTINGS]. */
  @Test
  fun `a page without a priority lands in the language band`() {
    maskPages(
      page("key-language", "Key language", DisplayPriority.KEY_LANGUAGE_SETTINGS),
      TestPage("no-priority", "B without a priority"),
      page("language-a", "A language", DisplayPriority.LANGUAGE_SETTINGS),
      page("other", "C other", DisplayPriority.OTHER_SETTINGS),
    )
    maskPanelFactories()

    assertThat(childIds()).containsExactly(
      childId("ColorSchemeFont"),
      childId("ConsoleFont"),
      childId("key-language"),
      childId("language-a"),
      childId("no-priority"),
      childId("other"),
    )
  }

  /** A panel factory that is not [DisplayPrioritySortable] goes after every band. */
  @Test
  fun `a panel factory without a priority goes last`() {
    maskPages(page("other", "A other", DisplayPriority.OTHER_SETTINGS))
    maskPanelFactories(
      panelFactory("plain-b", "B plain factory"),
      panelFactory("plain-a", "A plain factory"),
    )

    assertThat(childIds()).containsExactly(
      childId("ColorSchemeFont"),
      childId("ConsoleFont"),
      childId("other"),
      childId("plain-a"),
      childId("plain-b"),
    )
  }

  /**
   * The child id is the contract with the remote development host filter, see
   * `BackendConfigurablesStorage.filterConfigurables`, so it is pinned on its own.
   */
  @Test
  fun `the child id is the parent id and the page id`() {
    maskPages(page("some-page", "Some page", DisplayPriority.OTHER_SETTINGS))
    maskPanelFactories()

    assertThat(childIds()).contains("reference.settingsdialog.IDE.editor.colors.some-page")
  }

  /**
   * The snapshot of the pages that this test module registers.
   *
   * The assertion allows an extra child, because another test in the same JVM may register a page for good
   * through `ColorSettingsPages.registerPage`. It allows no reorder and no missing child, which is the point.
   * Update the list when a page joins the platform on purpose, and never to make a migration step pass.
   */
  @Test
  fun `the registered pages keep their order`() {
    assertThat(childIds()).containsSubsequence(*REGISTERED_CHILD_IDS)
  }

  private fun childIds(): List<String> {
    val options = ColorAndFontOptions()
    try {
      return options.configurables.map { (it as SearchableConfigurable).id }
    }
    finally {
      options.disposeUIResources()
    }
  }

  /**
   * Masks both page points, so a case that states an exact child list holds whatever the plugin set declares.
   * A page of either point reaches the tree through [ColorSettingsPageCatalog], so both must be empty here.
   */
  private fun maskPages(vararg pages: ColorSettingsPage) {
    ExtensionTestUtil.maskExtensions(ColorSettingsPage.EP_NAME, pages.toList(), disposable)
    ExtensionTestUtil.maskExtensions(ColorSettingsPageEP.EP_NAME, emptyList(), disposable)
  }

  private fun maskPanelFactories(vararg factories: ColorAndFontPanelFactory) {
    ExtensionTestUtil.maskExtensions(ColorAndFontPanelFactory.EP_NAME, factories.toList(), disposable)
  }

  private fun page(id: String, name: String, priority: DisplayPriority, weight: Int = 0): ColorSettingsPage =
    SortedTestPage(id, name, priority, weight)

  private fun panelFactory(id: String, name: String): ColorAndFontPanelFactory = TestPanelFactory(id, name)

  private fun childId(pageId: String): String = ColorAndFontOptions.ID + "." + pageId

  private open class TestPage(private val pageId: String, private val name: String) : ColorSettingsPage {
    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getDemoText(): String = ""

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = emptyArray()

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = name

    override fun getId(): String = pageId
  }

  private class SortedTestPage(
    pageId: String,
    name: String,
    private val priority: DisplayPriority,
    private val weight: Int,
  ) : TestPage(pageId, name), DisplayPrioritySortable {
    override fun getPriority(): DisplayPriority = priority

    override fun getWeight(): Int = weight
  }

  private class TestPanelFactory(private val id: String, private val name: String) : ColorAndFontPanelFactory {
    override fun createPanel(options: ColorAndFontOptions): NewColorAndFontPanel =
      error("The order test builds no panel")

    override fun getPanelDisplayName(): String = name

    override fun getConfigurableId(): String = id
  }
}

/**
 * The child ids of `reference.settingsdialog.IDE.editor.colors`, in the order the settings tree shows them,
 * in the plugin set of `intellij.platform.tests`.
 */
private val REGISTERED_CHILD_IDS: Array<String> = arrayOf(
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.options.colors.pages.GeneralColorsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.options.colors.pages.DefaultLanguageColorsPage",
  "reference.settingsdialog.IDE.editor.colors.ColorSchemeFont",
  "reference.settingsdialog.IDE.editor.colors.ConsoleFont",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.terminal.ANSIColoredConsoleColorsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.xdebugger.impl.ui.DebuggerColorsPage",
  "reference.settingsdialog.IDE.editor.colors.DiffAndMerge",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.options.colors.pages.CustomColorsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.vcs.actions.VcsColorsPageFactory",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.java.frontend.codeInsight.highlighting.JavaColorSettingsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.options.colors.pages.HTMLColorsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.json.highlighting.JsonColorsPage",
  "reference.settingsdialog.IDE.editor.colors.org.intellij.plugins.markdown.highlighting.MarkdownColorSettingsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.lang.properties.PropertiesColorsPage",
  "reference.settingsdialog.IDE.editor.colors.org.intellij.lang.regexp.RegExpColorsPage",
  "reference.settingsdialog.IDE.editor.colors.com.intellij.openapi.options.colors.pages.XMLColorsPage",
  "reference.settingsdialog.IDE.editor.colors.org.jetbrains.yaml.YAMLColorsPage",
  "reference.settingsdialog.IDE.editor.colors.ByScope",
  "reference.settingsdialog.IDE.editor.colors.org.intellij.images.options.impl.ImageEditorColorSchemeSettings",
)
