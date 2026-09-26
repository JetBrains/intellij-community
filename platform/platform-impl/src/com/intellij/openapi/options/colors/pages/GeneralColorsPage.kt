// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages

import com.intellij.application.options.colors.InspectionColorSettingsPage
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateColors
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.intellij.ui.EditorCustomization
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.ScrollBarPainter
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

open class GeneralColorsPage : ColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable, EditorCustomization {
  override fun getDisplayName(): String {
    return displayNameText
  }

  override fun getIcon(): Icon? {
    return FileTypes.PLAIN_TEXT.icon
  }

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
    return if (!Registry.`is`("live.templates.highlight.all.variables")) {
      ATT_DESCRIPTORS.filter { EditorColors.LIVE_TEMPLATE_INACTIVE_SEGMENT != it.key }
        .toTypedArray<AttributesDescriptor>()
    }
    else ATT_DESCRIPTORS
  }

  override fun getColorDescriptors(): Array<ColorDescriptor> {
    return if (ExperimentalUI.isNewUI()) NEW_COLOR_DESCRIPTORS else COLOR_DESCRIPTORS
  }

  override fun getHighlighter(): SyntaxHighlighter {
    return PlainSyntaxHighlighter()
  }

  override fun getDemoText(): String {
    return BASE_DEMO_TEXT + customSeveritiesDemoText
  }

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? {
    return additionalHighlightDescriptors
  }

  override fun getAdditionalHighlightingTagToColorKeyMap(): Map<String, ColorKey> {
    return ADDITIONAL_COLOR_KEY_MAPPING
  }

  override fun getPriority(): DisplayPriority {
    return DisplayPriority.GENERAL_SETTINGS
  }

  override fun customize(editor: EditorEx) {
    editor.settings.setSoftMargins(mutableListOf(50, 70))
    val foldPos = editor.document.text.indexOf(STRING_TO_FOLD)
    if (foldPos >= 0) {
      val foldingModel = editor.foldingModel
      foldingModel.runBatchFoldingOperation {
        foldingModel.createFoldRegion(foldPos, foldPos + STRING_TO_FOLD.length, STRING_TO_FOLD, null, true)
      }
    }
  }

  companion object {
    private val STRING_TO_FOLD = OptionsBundle.message("settings.color_scheme.general.demo.folded.text.highlighted")
    private val BASE_DEMO_TEXT = """<todo>${OptionsBundle.message("settings.color_scheme.general.demo.todo")}</todo>
${OptionsBundle.message("settings.color_scheme.general.demo.link.jetbrains.homePage")} <hyperlink_f>http://www.jetbrains.com</hyperlink_f>
${OptionsBundle.message("settings.color_scheme.general.demo.link.jetbrains.developerCommunity")} <hyperlink>https://www.jetbrains.com/devnet</hyperlink>
<ref_hyperlink>${OptionsBundle.message("settings.color_scheme.general.demo.hyperlink.reference")}</ref_hyperlink>
${OptionsBundle.message("settings.color_scheme.general.demo.hyperlink.inactive")} "<inactive_hyperlink>http://jetbrains.com</inactive_hyperlink>"

${OptionsBundle.message("settings.color_scheme.general.demo.search.title")}
  <search_result_wr>${OptionsBundle.message("settings.color_scheme.general.demo.search.result")}</search_result_wr> = "<search_text>${OptionsBundle.message("settings.color_scheme.general.demo.search.text")}</search_text>, <search_text>${OptionsBundle.message("settings.color_scheme.general.demo.search.text")}</search_text>, <search_text>${OptionsBundle.message("settings.color_scheme.general.demo.search.text")}</search_text>";
  <identifier_write>${OptionsBundle.message("settings.color_scheme.general.demo.search.identifier")}</identifier_write> = <search_result>${OptionsBundle.message("settings.color_scheme.general.demo.search.result")}</search_result>
  ${OptionsBundle.message("settings.color_scheme.general.demo.search.return")} <identifier>${OptionsBundle.message("settings.color_scheme.general.demo.search.identifier")};</identifier>

<folded_text>${OptionsBundle.message("settings.color_scheme.general.demo.folded.text")}</folded_text>
<folded_text_with_highlighting>$STRING_TO_FOLD</folded_text_with_highlighting>
<deleted_text>${OptionsBundle.message("settings.color_scheme.general.demo.deleted.text")}</deleted_text>
${OptionsBundle.message("settings.color_scheme.general.demo.template.live")} <template_active>${OptionsBundle.message("settings.color_scheme.general.demo.template.live.active")}</template_active> <template_inactive>${OptionsBundle.message("settings.color_scheme.general.demo.template.live.inactive")}</template_inactive> <template_var>${"$"}${OptionsBundle.message("settings.color_scheme.general.demo.template.live.variable")}$</template_var>
${OptionsBundle.message("settings.color_scheme.general.demo.injected_language")} <injected_lang>\.(gif|jpg|png)$</injected_lang>

${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.title")}
  <error>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.error")}</error>
  <warning>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.warning")}</warning>
  <weak_warning>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.warning.weak")}</weak_warning>
  <deprecated>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.deprecated")}</deprecated>
  <for_removal>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.for_removal")}</for_removal>
  <unused>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.unused")}</unused>
  <wrong_ref>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.wrong_ref")}</wrong_ref>
  <runtime_error>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.runtime_error")}</runtime_error>
  <server_error>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.server_error")}</server_error>
  <server_duplicate>${OptionsBundle.message("settings.color_scheme.general.demo.code_inspections.server_duplicate")}</server_duplicate>
"""
    private val ATT_DESCRIPTORS = arrayOf(
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.default.text"), HighlighterColors.TEXT),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.folded.text"), EditorColors.FOLDED_TEXT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.deleted.text"),
                           EditorColors.DELETED_TEXT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result"),
                           EditorColors.SEARCH_RESULT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result.write.access"),
                           EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret"),
                           EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret.write"),
                           EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.text.search.result"),
                           EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.live.template.active"),
                           EditorColors.LIVE_TEMPLATE_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.live.template.inactive"),
                           EditorColors.LIVE_TEMPLATE_INACTIVE_SEGMENT),
      AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.template.variable"),
                           TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.injected.language.fragment"),
                           EditorColors.INJECTED_LANGUAGE_FRAGMENT),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.hyperlink.new"), CodeInsightColors.HYPERLINK_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.hyperlink.followed"),
                           CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.reference.hyperlink"),
                           EditorColors.REFERENCE_HYPERLINK_COLOR),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.hyperlink.inactive"),
                           CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.matched.brace"),
                           CodeInsightColors.MATCHED_BRACE_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.unmatched.brace"),
                           CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.todo.defaults"),
                           CodeInsightColors.TODO_DEFAULT_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.bookmarks"), CodeInsightColors.BOOKMARKS_ATTRIBUTES),
      AttributesDescriptor(OptionsBundle.message("options.java.color.descriptor.full.coverage"), CodeInsightColors.LINE_FULL_COVERAGE),
      AttributesDescriptor(OptionsBundle.message("options.java.color.descriptor.partial.coverage"),
                           CodeInsightColors.LINE_PARTIAL_COVERAGE),
      AttributesDescriptor(OptionsBundle.message("options.java.color.descriptor.none.coverage"), CodeInsightColors.LINE_NONE_COVERAGE),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.breadcrumbs.default"), EditorColors.BREADCRUMBS_DEFAULT),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.breadcrumbs.hovered"), EditorColors.BREADCRUMBS_HOVERED),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.breadcrumbs.current"), EditorColors.BREADCRUMBS_CURRENT),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.breadcrumbs.inactive"),
                           EditorColors.BREADCRUMBS_INACTIVE),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs.selected.tab"), EditorColors.TAB_SELECTED),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs.selected.tab.inactive"),
                           EditorColors.TAB_SELECTED_INACTIVE),
      AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.lens"), EditorColors.CODE_LENS_BORDER_COLOR)
    )
    private val COLOR_DESCRIPTORS = arrayOf(
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.background.in.readonly.files"),
                      EditorColors.READONLY_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.readonly.fragment.background"),
                      EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.gutter.background"), EditorColors.GUTTER_BACKGROUND,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.notification.background"),
                      EditorColors.NOTIFICATION_BACKGROUND, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.background"),
                      EditorColors.SELECTION_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.background.inactive"),
                      EditorColors.SELECTION_BACKGROUND_COLOR_INACTIVE, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.foreground"),
                      EditorColors.SELECTION_FOREGROUND_COLOR, ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.sticky.lines.background"),
                      EditorColors.STICKY_LINES_BACKGROUND, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.sticky.lines.hovered.color"),
                      EditorColors.STICKY_LINES_HOVERED_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.sticky.lines.border.color"),
                      EditorColors.STICKY_LINES_BORDER_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.scrollbar.thumb.while.scrolling"),
                      ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND, ColorDescriptor.Kind.BACKGROUND_WITH_TRANSPARENCY),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.scrollbar.thumb"),
                      ScrollBarPainter.THUMB_OPAQUE_BACKGROUND, ColorDescriptor.Kind.BACKGROUND_WITH_TRANSPARENCY),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs.selected.underline"), EditorColors.TAB_UNDERLINE,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs.selected.underline.inactive"),
                      EditorColors.TAB_UNDERLINE_INACTIVE, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs.modified.icon.color"),
                      EditorColors.MODIFIED_TAB_ICON_COLOR, ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret"), EditorColors.CARET_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret.row"), EditorColors.CARET_ROW_COLOR,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.right.margin"), EditorColors.RIGHT_MARGIN_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.whitespaces"), EditorColors.WHITESPACES_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tabs"), EditorColors.TABS_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.indent.guide"), EditorColors.INDENT_GUIDE_COLOR,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.indent.guide.selected"),
                      EditorColors.SELECTED_INDENT_GUIDE_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.matched.braces.indent.guide"),
                      EditorColors.MATCHED_BRACES_INDENT_GUIDE_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.line.number"), EditorColors.LINE_NUMBERS_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.line.number.on.caret.row"),
                      EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR, ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tearline"), EditorColors.TEARLINE_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.tearline.selected"), EditorColors.SELECTED_TEARLINE_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.java.color.descriptor.method.separator.color"),
                      CodeInsightColors.METHOD_SEPARATORS_COLOR, ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.soft.wrap.sign"), EditorColors.SOFT_WRAP_SIGN_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.documentation"), EditorColors.DOCUMENTATION_COLOR,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.lookup"), Lookup.LOOKUP_COLOR,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.information"), HintUtil.INFORMATION_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.question"), HintUtil.QUESTION_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.warning"), HintUtil.WARNING_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.error"), HintUtil.ERROR_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.hint_border"), HintUtil.HINT_BORDER_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.recent.locations.selection"),
                      HintUtil.RECENT_LOCATIONS_SELECTION_KEY, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.tooltip"), IdeTooltipManager.TOOLTIP_COLOR_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.popups.promotion"), HintUtil.PROMOTION_PANE_KEY,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.visual.guides"), EditorColors.VISUAL_INDENT_GUIDE_COLOR,
                      ColorDescriptor.Kind.FOREGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.highlighted.folding.border"),
                      EditorColors.FOLDED_TEXT_BORDER_COLOR, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.breadcrumbs.border"), EditorColors.BREADCRUMBS_BORDER_COLOR,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.preview.background"), EditorColors.PREVIEW_BACKGROUND,
                      ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.preview.border.color"), EditorColors.PREVIEW_BORDER_COLOR,
                      ColorDescriptor.Kind.BACKGROUND))
    private val NEW_COLOR_DESCRIPTORS by lazy { patchDescriptorsForNewUI(COLOR_DESCRIPTORS) }
    private val BASE_ADDITIONAL_HIGHLIGHT_DESCRIPTORS: Map<String, TextAttributesKey> = LinkedHashMap<String, TextAttributesKey>().apply {
      put("folded_text", EditorColors.FOLDED_TEXT_ATTRIBUTES)
      put("folded_text_with_highlighting", CodeInsightColors.WARNINGS_ATTRIBUTES)
      put("deleted_text", EditorColors.DELETED_TEXT_ATTRIBUTES)
      put("search_result", EditorColors.SEARCH_RESULT_ATTRIBUTES)
      put("search_result_wr", EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES)
      put("search_text", EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES)
      put("identifier", EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES)
      put("identifier_write", EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES)
      put("template_active", EditorColors.LIVE_TEMPLATE_ATTRIBUTES)
      put("template_var", TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES)
      put("injected_lang", EditorColors.INJECTED_LANGUAGE_FRAGMENT)
      put("todo", CodeInsightColors.TODO_DEFAULT_ATTRIBUTES)
      put("hyperlink", CodeInsightColors.HYPERLINK_ATTRIBUTES)
      put("hyperlink_f", CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES)
      put("ref_hyperlink", EditorColors.REFERENCE_HYPERLINK_COLOR)
      put("inactive_hyperlink", CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
      put("wrong_ref", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
      put("deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES)
      put("for_removal", CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES)
      put("unused", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
      put("error", CodeInsightColors.ERRORS_ATTRIBUTES)
      put("warning", CodeInsightColors.WARNINGS_ATTRIBUTES)
      put("weak_warning", CodeInsightColors.WEAK_WARNING_ATTRIBUTES)
      put("server_error", CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING)
      put("server_duplicate", CodeInsightColors.DUPLICATE_FROM_SERVER)
      put("runtime_error", CodeInsightColors.RUNTIME_ERROR)
    }

    private val additionalHighlightDescriptors: @NonNls Map<String, TextAttributesKey>
      get() {
        val descriptors = LinkedHashMap(BASE_ADDITIONAL_HIGHLIGHT_DESCRIPTORS)
        if (Registry.`is`("live.templates.highlight.all.variables")) {
          descriptors["template_inactive"] = EditorColors.LIVE_TEMPLATE_INACTIVE_SEGMENT
        }
        for (provider in SeveritiesProvider.EP_NAME.extensionList) {
          for (highlightInfoType in provider.severitiesHighlightInfoTypes) {
            descriptors[getHighlightDescTagName(highlightInfoType)] = highlightInfoType.attributesKey
          }
        }
        return descriptors
      }

    private val ADDITIONAL_COLOR_KEY_MAPPING: MutableMap<String, ColorKey> = HashMap()

    init {
      ADDITIONAL_COLOR_KEY_MAPPING["folded_text_with_highlighting"] = EditorColors.FOLDED_TEXT_BORDER_COLOR
    }

    private fun patchDescriptorsForNewUI(descriptors: Array<ColorDescriptor>): Array<ColorDescriptor> {
      if (!ExperimentalUI.isNewUI()) return descriptors
      for (i in descriptors.indices) {
        if (descriptors[i].key === EditorColors.GUTTER_BACKGROUND) {
          descriptors[i] = ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.gutter.background"),
                                           EditorColors.EDITOR_GUTTER_BACKGROUND, ColorDescriptor.Kind.BACKGROUND)
          break
        }
      }
      return descriptors
    }

    private val customSeveritiesDemoText: String
      get() {
        val buff = StringBuilder()
        for (provider in SeveritiesProvider.EP_NAME.extensionList) {
          for (highlightInfoType in provider.severitiesHighlightInfoTypes) {
            val tag = getHighlightDescTagName(highlightInfoType)
            buff.append("  <").append(tag).append(">")
            buff.append(StringUtil.toLowerCase(tag))
            buff.append("</").append(tag).append(">").append("\n")
          }
        }
        return buff.toString()
      }

    private fun getHighlightDescTagName(highlightInfoType: HighlightInfoType): String {
      return highlightInfoType.getSeverity(null).myName
    }

    val displayNameText: @NlsContexts.ConfigurableName String
      get() = OptionsBundle.message("options.general.display.name")
  }
}
