// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_CENTERED
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_GRAYED
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
import com.intellij.openapi.editor.impl.EditorCssFontResolver.EDITOR_FONT_NAME_PLACEHOLDER
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration.*
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StyleSheetUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.awt.Color
import java.lang.Integer.toHexString
import java.util.*
import javax.swing.UIManager
import javax.swing.text.html.StyleSheet
import kotlin.math.roundToInt

@ApiStatus.Internal
const val CODE_BLOCK_CLASS: String = "code-block"

/**
 * Provides list of default CSS rules for JBHtmlPane
 */
@Suppress("UseJBColor", "CssInvalidHtmlTagReference", "CssInvalidPropertyValue", "CssUnusedSymbol")
@Service(Service.Level.APP)
internal class JBHtmlPaneStyleSheetRulesProvider {

  fun getStyleSheet(paneBackgroundColor: Color, scaleFactor: Float, baseFontSize: Int, configuration: JBHtmlPaneStyleConfiguration): StyleSheet =
    styleSheetCache.get(JBHtmlPaneStylesheetParameters(paneBackgroundColor.rgb and 0xffffff, scaleFactor,
                                                       baseFontSize, JBHtmlPaneStyleConfigurationSnapshot(configuration)))

  init {
    // Editor color scheme can be referenced from JBHtmlPaneStyleConfiguration,
    // through linked stylesheets e.g., EditorColorsSchemeStyleSheet,
    // which can contain references to projects and editors.
    // Drop caches if projects or editors are closed to avoid memory leaks.
    val messageBus = ApplicationManager.getApplication().messageBus.connect()
    messageBus.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosed(project: Project) {
        invalidateCache()
      }
    })
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        invalidateCache()
      }
    }, messageBus)
    // Drop caches on global colors scheme change
    messageBus.subscribe(EditorColorsManager.TOPIC, object : EditorColorsListener {
      override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        invalidateCache()
      }
    })
  }

  private fun invalidateCache() {
    styleSheetCache.invalidateAll()
  }

  private val inlineCodeStyling = ControlColorStyleBuilder(
    ElementKind.CodeInline,
    defaultBackgroundColor = Color(0x5A5D6B),
    defaultBackgroundOpacity = 10,
    defaultBorderRadius = 10,
    fallbackToEditorForeground = true,
  )

  private val blockCodeStyling = ControlColorStyleBuilder(
    ElementKind.CodeBlock,
    defaultBorderColor = Color(0xEBECF0),
    defaultBorderRadius = 10,
    defaultBorderWidth = 1,
    fallbackToEditorBackground = true,
    fallbackToEditorForeground = true,
  )

  private val shortcutStyling = ControlColorStyleBuilder(
    ElementKind.Shortcut,
    defaultBorderColor = Color(0xA8ADBD),
    defaultBorderRadius = 7,
    defaultBorderWidth = 1,
    fallbackToEditorForeground = true,
    fallbackToEditorBorder = true,
  )

  private val styleSheetCache: LoadingCache<JBHtmlPaneStylesheetParameters, StyleSheet> = Caffeine.newBuilder()
    .maximumSize(20)
    .build { (bgColor, scaleFactor, baseFontSize, configuration) -> buildStyleSheet(Color(bgColor), { (it * scaleFactor).roundToInt() }, baseFontSize, configuration) }

  private fun buildStyleSheet(paneBackgroundColor: Color, scale: (Int) -> Int, baseFontSize: Int, configuration: JBHtmlPaneStyleConfigurationSnapshot): StyleSheet =
    StyleSheetUtil.loadStyleSheet(sequenceOf(
      getDefaultFormattingStyles(configuration, scale, baseFontSize),
      getCodeRules(paneBackgroundColor, configuration, scale),
      getShortcutRules(paneBackgroundColor, configuration, scale)
    ).joinToString("\n"))

  private fun getDefaultFormattingStyles(
    configuration: JBHtmlPaneStyleConfigurationSnapshot,
    scale: (Int) -> Int,
    baseFontSize: Int,
  ): String {
    val spacingBefore = scale(configuration.spaceBeforeParagraph)
    val spacingAfter = scale(configuration.spaceAfterParagraph)
    val hrColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Tooltip.separatorColor())
    val grayedForeground = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Tooltip.grayedForeground())
    val paragraphSpacing = """padding: ${spacingBefore}px 0 ${spacingAfter}px 0"""

    @Language("CSS")
    val styles = """
      body, p, p-implied, li, ol, ul, th, tr, td, table { font-size: ${scale(baseFontSize)} }
      h6 { font-size: ${scale(baseFontSize + 1)} }
      h5 { font-size: ${scale(baseFontSize + 2)} }
      h4 { font-size: ${scale(baseFontSize + 3)} }
      h3 { font-size: ${scale(baseFontSize + 4)} }
      h2 { font-size: ${scale(baseFontSize + 6)} }
      h1 { font-size: ${scale(baseFontSize + 8)} }
      h1, h2, h3, h4, h5, h6 {margin: ${scale(4)}px 0 0 0; ${paragraphSpacing}; }
      p { margin: 0 0 0 0; ${paragraphSpacing}; line-height: 125%; }
      ul { margin: 0 0 0 ${scale(10)}px; ${paragraphSpacing};}
      ol { margin: 0 0 0 ${scale(20)}px; ${paragraphSpacing};}
      li { padding: ${scale(4)}px 0 ${scale(2)}px 0; }
      details { margin: ${spacingBefore}px 0 0 0; }
      summary { margin: 0 0 ${spacingBefore}px 0; }
      details p { margin: 0 0 0 ${scale(10)}px; }
      li p, li p-implied { padding-top: 0; padding-bottom: 0; line-height: 125%; }
      th { text-align: left; }
      tr, table { margin: 0 0 0 0; padding: 0 0 0 0; }
      td { margin: 0 0 0 0; padding: ${spacingBefore}px ${spacingBefore + spacingAfter}px ${spacingAfter}px 0; }
      td p { padding-top: 0; padding-bottom: 0; }
      td pre { padding: ${scale(1)}px 0 0 0; margin: 0 0 0 0 }
      blockquote { 
          padding: 0 0 0 ${scale(10)}px; 
          border-left: ${grayedForeground} solid ${scale(2)}px;
          margin: ${spacingBefore}px 0 ${spacingAfter}px 0;
      }
      blockquote p { border: none; }
      .$CLASS_CENTERED { text-align: center }
      .$CLASS_GRAYED { color: ${grayedForeground}}
      hr { 
         padding: ${scale(1)}px 0 0 0; 
         margin: ${spacingBefore} 0 ${spacingAfter}px 0; 
         border-bottom: ${scale(1)}px solid $hrColor; 
         width: 100%;
      }
      p hr {
         margin: ${spacingAfter + spacingBefore} 0 ${spacingAfter + spacingBefore}px 0; 
      }
    """.trimIndent()
    return styles
  }

  private fun getMonospaceFontSizeCorrection(inlineCode: Boolean): Int =
    @Suppress("DEPRECATION", "removal")
    // TODO: When removing `getMonospaceFontSizeCorrection` copy it's code here
    DocumentationSettings.getMonospaceFontSizeCorrection(inlineCode)

  private fun getShortcutRules(
    paneBackgroundColor: Color,
    configuration: JBHtmlPaneStyleConfigurationSnapshot,
    scale: (Int) -> Int,
  ): String {
    val fontName = if (configuration.useFontLigaturesInCode) EDITOR_FONT_NAME_PLACEHOLDER else EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
    val contentCodeFontSizePercent = getMonospaceFontSizeCorrection(true)

    @Language("CSS")
    val result = """
      kbd { 
        font-size: ${contentCodeFontSizePercent}%; 
        font-family:"$fontName"; 
        padding: ${scale(1)}px ${scale(6)}px; 
        margin: ${scale(1)}px 0px;
        ${shortcutStyling.getCssStyle(paneBackgroundColor, configuration, scale)}
      }
      """.trimIndent()
    return result
  }

  private fun getCodeRules(
    paneBackgroundColor: Color,
    configuration: JBHtmlPaneStyleConfigurationSnapshot,
    scale: (Int) -> Int,
  ): String {
    val result = mutableListOf<String>()
    val spacingBefore = scale(configuration.spaceBeforeParagraph)
    val spacingAfter = scale(configuration.spaceAfterParagraph)

    val definitionCodeFontSizePercent = getMonospaceFontSizeCorrection(false)
    val contentCodeFontSizePercent = getMonospaceFontSizeCorrection(true)

    val fontName = if (configuration.useFontLigaturesInCode) EDITOR_FONT_NAME_PLACEHOLDER else EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
    result.addAllIfNotNull(
      "tt, code, samp, pre, .pre { font-family:\"$fontName\"; font-size:$contentCodeFontSizePercent%; }",
    )
    result.add("samp { font-weight: bold }")
    if (configuration.largeCodeFontSizeSelectors.isNotEmpty()) {
      result.add("${configuration.largeCodeFontSizeSelectors.joinToString(", ")} { font-size: $definitionCodeFontSizePercent% }")
    }
    if (configuration.enableInlineCodeBackground) {
      val selectors = configuration.inlineCodeParentSelectors.asSequence().map { "$it code" }.joinToString(", ")
      result.add("$selectors { ${inlineCodeStyling.getCssStyle(paneBackgroundColor, configuration, scale)} }")
      result.add("$selectors { padding: ${scale(1)}px ${scale(4)}px; margin: ${scale(1)}px 0px; }")
    }
    if (configuration.enableCodeBlocksBackground) {
      val blockCodeStyling = if (configuration.editorInlineContext)
        blockCodeStyling.copy(
          defaultBackgroundColor = Color(0x5A5D6B),
          defaultBackgroundOpacity = 4,
        )
      else
        blockCodeStyling
      result.add("div.code-block { ${blockCodeStyling.getCssStyle(paneBackgroundColor, configuration, scale)} }")
      result.add("div.code-block { margin: ${spacingBefore}px 0 ${spacingAfter}px 0; padding: ${scale(10)}px ${scale(13)}px ${scale(10)}px ${scale(13)}px; }")
      result.add("div.code-block pre { padding: 0px; margin: 0px; line-height: 120%; }")
    }
    return result.joinToString("\n")
  }

  companion object {

    internal fun buildCodeBlock(childNodes: List<Node>): Element =
      Element("div").addClass(CODE_BLOCK_CLASS).appendChild(
        Element("pre")
          .attr("style", "padding: 0px; margin: 0px")
          .insertChildren(0, childNodes)
      )

    private fun toHtmlColor(color: Color): String =
      ColorUtil.toHex(color)
  }

  private data class JBHtmlPaneStylesheetParameters(
    val bgColor: Int,
    val scaleFactor: Float,
    val baseFontSize: Int,
    val configuration: JBHtmlPaneStyleConfigurationSnapshot,
  )

  private data class ControlColorStyleBuilder(
    val elementKind: ElementKind,
    val defaultBackgroundColor: Color? = null,
    val defaultBackgroundOpacity: Int = 100,
    val defaultForegroundColor: Color? = null,
    val defaultBorderColor: Color? = null,
    val defaultBorderWidth: Int = 0,
    val defaultBorderRadius: Int = 0,
    val fallbackToEditorBackground: Boolean = false,
    val fallbackToEditorForeground: Boolean = false,
    val fallbackToEditorBorder: Boolean = false,
  ) {

    private fun getBackgroundColor(configuration: JBHtmlPaneStyleConfigurationSnapshot): Color? = getColor(configuration, ElementProperty.BackgroundColor)

    private fun getForegroundColor(configuration: JBHtmlPaneStyleConfigurationSnapshot): Color? = getColor(configuration, ElementProperty.ForegroundColor)

    private fun getBorderColor(configuration: JBHtmlPaneStyleConfigurationSnapshot): Color? = getColor(configuration, ElementProperty.BorderColor)

    private fun getBackgroundOpacity(configuration: JBHtmlPaneStyleConfigurationSnapshot): Int? = getInt(configuration, ElementProperty.BackgroundOpacity)

    private fun getBorderWidth(configuration: JBHtmlPaneStyleConfigurationSnapshot): Int? = getInt(configuration, ElementProperty.BorderWidth)

    private fun getBorderRadius(configuration: JBHtmlPaneStyleConfigurationSnapshot): Int? = getInt(configuration, ElementProperty.BorderRadius)

    fun getCssStyle(
      editorPaneBackgroundColor: Color,
      configuration: JBHtmlPaneStyleConfigurationSnapshot,
      scale: (Int) -> Int,
    ): String {
      val result = StringBuilder()

      if (configuration.editorInlineContext) {
        val attributes = configuration.colorScheme.getAttributes(elementKind)
        if (attributes != null) {
          attributes.backgroundColor?.let { result.append("background-color: #${toHtmlColor(it)};") }
          attributes.foregroundColor?.let { result.append("color: #${toHtmlColor(it)};") }
          if (attributes.effectType == EffectType.BOXED && attributes.effectColor != null) {
            result.append("border-color: #${toHtmlColor(attributes.effectColor)};")
            result.append(getBorderWidth(configuration)?.takeIf { it > 0 }, 1) {
              "border-width: ${scale(it)}px;"
            }
          }
          result.append(getBorderRadius(configuration), defaultBorderRadius) {
            "border-radius: ${scale(it)}px;"
          }
          return result.toString()
        }
      }

      val editorColorsScheme = configuration.colorScheme

      result.append(
        getBackgroundColor(configuration),
        defaultBackgroundColor,
        editorColorsScheme.takeIf { fallbackToEditorBackground }?.defaultBackground
      ) {
        val opacity = choose(getBackgroundOpacity(configuration), defaultBackgroundOpacity) ?: 100
        val background = mixColors(editorPaneBackgroundColor, it, opacity)
        "background-color: #${toHtmlColor(background)};"
      }

      result.append(
        getForegroundColor(configuration),
        defaultForegroundColor,
        editorColorsScheme.takeIf { fallbackToEditorForeground }?.defaultForeground
      ) {
        "color: #${toHtmlColor(it)};"
      }

      result.append(
        getBorderColor(configuration),
        defaultBorderColor,
        editorColorsScheme.takeIf { fallbackToEditorBorder }?.defaultForeground
      ) {
        "border-color: #${toHtmlColor(it)};"
      }

      result.append(getBorderWidth(configuration), defaultBorderWidth) {
        "border-width: ${scale(it)}px;"
      }

      result.append(getBorderRadius(configuration), defaultBorderRadius) {
        "border-radius: ${scale(it)}px;"
      }

      return result.toString()
    }

    private fun <T : Any> choose(themeVersion: T?, defaultVersion: T?, editorVersion: T? = null): T? =
      themeVersion ?: defaultVersion ?: editorVersion

    private fun <T : Any> StringBuilder.append(themeVersion: T?, defaultVersion: T?, editorVersion: T? = null, mapper: (T) -> String) {
      choose(themeVersion, defaultVersion, editorVersion)?.let(mapper)?.let { this.append(it) }
    }

    private fun mixColors(c1: Color, c2: Color, opacity2: Int): Color {
      if (opacity2 >= 100) return c2
      if (opacity2 <= 0) return c1
      return Color(
        ((100 - opacity2) * c1.red + opacity2 * c2.red) / 100,
        ((100 - opacity2) * c1.green + opacity2 * c2.green) / 100,
        ((100 - opacity2) * c1.blue + opacity2 * c2.blue) / 100
      )
    }

    private fun getColor(configuration: JBHtmlPaneStyleConfigurationSnapshot, property: ElementProperty): Color? =
      UIManager.getColor(getKey(configuration, property))

    private fun getInt(configuration: JBHtmlPaneStyleConfigurationSnapshot, property: ElementProperty): Int? =
      UIManager.get(getKey(configuration, property)) as Int?

    private fun getKey(configuration: JBHtmlPaneStyleConfigurationSnapshot, property: ElementProperty): String {
      val themeOverrides = configuration.elementStyleOverrides
      val suffix = if (themeOverrides != null && themeOverrides.overrides[elementKind]?.contains(property) == true) {
        "." + themeOverrides.elementKindThemePropertySuffix
      }
      else ""
      return "${elementKind.id}$suffix.${property.id}"
    }

  }

}

private class JBHtmlPaneStyleConfigurationSnapshot(configuration: JBHtmlPaneStyleConfiguration) {

  val colorScheme: EditorColorsSchemeSnapshot = EditorColorsSchemeSnapshot(configuration.colorSchemeProvider())
  val editorInlineContext: Boolean = configuration.editorInlineContext
  val inlineCodeParentSelectors: List<String> = configuration.inlineCodeParentSelectors
  val largeCodeFontSizeSelectors: List<String> = configuration.largeCodeFontSizeSelectors
  val enableInlineCodeBackground: Boolean = configuration.enableInlineCodeBackground
  val enableCodeBlocksBackground: Boolean = configuration.enableCodeBlocksBackground
  val useFontLigaturesInCode: Boolean = configuration.useFontLigaturesInCode
  val spaceBeforeParagraph: Int = configuration.spaceBeforeParagraph
  val spaceAfterParagraph: Int = configuration.spaceAfterParagraph
  val elementStyleOverrides: ElementStyleOverrides? = configuration.elementStyleOverrides

  override fun toString(): String =
    "JBHtmlPaneStyleConfigurationSnapshot(\n  colorScheme=$colorScheme,\n  editorInlineContext=$editorInlineContext,\n  inlineCodeParentSelectors=$inlineCodeParentSelectors,\n  largeCodeFontSizeSelectors=$largeCodeFontSizeSelectors,\n  enableInlineCodeBackground=$enableInlineCodeBackground,\n  enableCodeBlocksBackground=$enableCodeBlocksBackground,\n  useFontLigaturesInCode=$useFontLigaturesInCode,\n  spaceBeforeParagraph=$spaceBeforeParagraph,\n  spaceAfterParagraph=$spaceAfterParagraph,\n  elementStyleOverrides=$elementStyleOverrides\n)"

  override fun equals(other: Any?): Boolean =
    other === this
    || other is JBHtmlPaneStyleConfigurationSnapshot
    && colorScheme == other.colorScheme
    && inlineCodeParentSelectors == other.inlineCodeParentSelectors
    && largeCodeFontSizeSelectors == other.largeCodeFontSizeSelectors
    && enableInlineCodeBackground == other.enableInlineCodeBackground
    && enableCodeBlocksBackground == other.enableCodeBlocksBackground
    && useFontLigaturesInCode == other.useFontLigaturesInCode
    && spaceBeforeParagraph == other.spaceBeforeParagraph
    && spaceAfterParagraph == other.spaceAfterParagraph

  override fun hashCode(): Int =
    Objects.hash(colorScheme,
                 inlineCodeParentSelectors, largeCodeFontSizeSelectors,
                 enableInlineCodeBackground, enableCodeBlocksBackground,
                 useFontLigaturesInCode, spaceBeforeParagraph, spaceAfterParagraph)
}

private class EditorColorsSchemeSnapshot(scheme: EditorColorsScheme) {

  val defaultBackground: Color = scheme.defaultBackground
  val defaultForeground: Color = scheme.defaultForeground

  private val attributes = ElementKind.entries.associateWith {
    scheme.getAttributes(it.colorSchemeKey, false)
  }

  fun getAttributes(kind: ElementKind): TextAttributes? =
    attributes[kind]

  override fun toString(): String =
    "EditorColorsSchemeSnapshot(\n    defaultBackground=#${toHexString(defaultBackground.rgb and 0xffffff)},\n    defaultForeground=#${toHexString(defaultForeground.rgb and 0xffffff)}, \n    attributes=$attributes\n  )"

  override fun hashCode(): Int =
    Objects.hash(
      defaultBackground.rgb and 0xffffff,
      defaultForeground.rgb and 0xffffff,
    )

  override fun equals(other: Any?): Boolean =
    // Update here when more colors are used from the colorScheme
    other === this
    || other is EditorColorsSchemeSnapshot
    && defaultBackground.rgb == other.defaultBackground.rgb
    && defaultForeground.rgb == other.defaultForeground.rgb
    && attributes == other.attributes
}
