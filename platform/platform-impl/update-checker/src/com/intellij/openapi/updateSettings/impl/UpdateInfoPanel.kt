// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.ProductIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogBackgroundImageProvider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.FontUtil
import com.intellij.util.IconUtil
import com.intellij.util.system.OS
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.math.max
import kotlin.math.min

internal class UpdateInfoPanel(
  val newBuild: BuildInfo,
  val patches: UpdateChain?,
  val testPatch: Path?,
  val writeProtected: Boolean,
  val licenseInfo: @NlsContexts.Label String?,
  val licenseWarn: Boolean,
  val enableLink: Boolean,
  val updatedChannel: UpdateChannel,
  val parentDisposable: Disposable,
) {
  companion object {
    private const val DEFAULT_MIN_HEIGHT = 300
    private const val DEFAULT_MAX_HEIGHT = 600
    private const val DEFAULT_WIDTH = 700
    private val DIVIDER_COLOR = JBColor(0xd9d9d9, 0x515151)
    private val PATCH_SIZE_RANGE: Regex = "from \\d+ to (\\d+)".toRegex()

    private val REPORTING_LISTENER = object : BrowserHyperlinkListener() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        UpdateInfoStatsCollector.click(e.description)
        super.hyperlinkActivated(e)
      }
    }

    @JvmStatic
    fun downloadUrl(newBuild: BuildInfo, updatedChannel: UpdateChannel): String =
      IdeUrlTrackingParametersProvider.getInstance().augmentUrl(
        newBuild.downloadUrl ?: newBuild.blogPost ?: updatedChannel.url
        ?: ExternalProductResourceUrls.getInstance().downloadPageUrl?.toExternalForm() ?: ApplicationInfo.getInstance().companyURL
        ?: "https://www.jetbrains.com")
  }

  val appInfo: ApplicationInfo = ApplicationInfo.getInstance()
  val appNames: ApplicationNamesInfo = ApplicationNamesInfo.getInstance()

  fun create(): JPanel {

    val textPane = JEditorPane("text/html", "")
    textPane.border = JBUI.Borders.empty(10, 16)
    textPane.isEditable = false
    textPane.caretPosition = 0
    textPane.text = textPaneContent(newBuild, updatedChannel, appNames)
    textPane.addHyperlinkListener(REPORTING_LISTENER)

    val scrollPane = ScrollPaneFactory.createScrollPane(textPane, true)
    scrollPane.border = JBUI.Borders.customLine(DIVIDER_COLOR, 0, 0, 1, 0)
    scrollPane.preferredSize = Dimension(
      min(scrollPane.preferredSize.width, DEFAULT_WIDTH),
      scrollPane.preferredSize.height.coerceIn(DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT))

    val infoPanel = JPanel(VerticalFlowLayout(0, 10))
    infoPanel.border = JBUI.Borders.empty(8, 16)

    if (licenseInfo != null) {
      val label = JBLabel(licenseInfo)
      label.foreground = if (licenseWarn) JBColor.RED else null
      label.font = smallFont(label.font)
      infoPanel.add(label)
    }

    if (writeProtected) {
      val label = JBLabel(IdeBundle.message("updates.write.protected", appNames.productName, PathManager.getHomePath()))
      label.foreground = JBColor.RED
      label.font = smallFont(label.font)
      infoPanel.add(label)
    }

    val infoRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    val infoLabel = JBLabel()
    infoLabel.foreground = SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.fgColor
    infoLabel.font = smallFont(infoLabel.font)
    infoLabel.text = infoLabelText(newBuild, patches, testPatch, appInfo)
    infoRow.add(infoLabel)
    if (enableLink) {
      val link = ActionLink(IdeBundle.message("updates.configure.updates.label")) {
        ShowSettingsUtil.getInstance().editConfigurable(infoRow, UpdateSettingsConfigurable(false))
      }
      link.border = JBUI.Borders.emptyLeft(4)
      link.font = smallFont(link.font)
      infoRow.add(link)
    }
    infoPanel.add(infoRow)

    val panel = JPanel(BorderLayout())
    panel.add(scrollPane, BorderLayout.CENTER)
    panel.add(infoPanel, BorderLayout.SOUTH)
    return panel
  }

  fun createNew(): JPanel {
    val productIcon = ProductIcons.getInstance().productIcon

    val mainPanel = BorderLayoutPanel().apply {
      border = JBUI.Borders.empty(8, 16)
      preferredSize = Dimension(DEFAULT_WIDTH, DEFAULT_MIN_HEIGHT)
    }

    val textPane = JEditorPane("text/html", "").apply {
      border = JBUI.Borders.empty()
      isEditable = false
      caretPosition = 0
      text = textPaneContent(newBuild, updatedChannel, appNames)
      addHyperlinkListener(REPORTING_LISTENER)
      isOpaque = false
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(textPane, true).apply {
      border = JBUI.Borders.customLine(DIVIDER_COLOR, 0, 0, 0, 0)
      isOpaque = false
      viewport.isOpaque = false
      preferredSize = Dimension(
        min(preferredSize.width, DEFAULT_WIDTH),
        preferredSize.height.coerceIn(DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT))
    }

    val infoPanel = panel {
      row {
        icon(IconUtil.scale(productIcon, null, 3f))
      }.customize(UnscaledGapsY(top = 0, bottom = 0))
      row {
        cell(scrollPane).align(AlignX.FILL)
      }
      if (licenseInfo != null) {
        row {
          label(licenseInfo).applyToComponent {
            foreground = if (licenseWarn) JBColor.RED else null
            font = smallFont(font)
          }
        }
      }
      if (writeProtected) {
        row {
          label(IdeBundle.message("updates.write.protected", appNames.productName, PathManager.getHomePath()))
            .applyToComponent {
              foreground = JBColor.RED
            }
        }
      }
    }

    val additionalInfoPanel = panel {
      customizeSpacingConfiguration(object : IntelliJSpacingConfiguration() {
        override val verticalComponentGap: Int get() = 2
      }) {
        row {
          text(infoLabelText(newBuild, patches, testPatch, appInfo))
            .applyToComponent {
              foreground = SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.fgColor
              font = smallFont(font)
            }
        }.bottomGap(BottomGap.NONE)
        if (enableLink) {
          row {
            link(IdeBundle.message("updates.configure.updates.label")) {
              ShowSettingsUtil.getInstance().editConfigurable(mainPanel, UpdateSettingsConfigurable(false))
            }.applyToComponent {
              font = smallFont(font)
            }
          }
        }
      }
    }

    mainPanel.addToCenter(infoPanel)
    mainPanel.addToBottom(additionalInfoPanel)
    val isDark = LafManager.getInstance().currentUIThemeLookAndFeel?.isDark ?: true
    val bgImage = DialogBackgroundImageProvider.getInstance().getImage(isDark)
    if (bgImage != null) {
      IdeBackgroundUtil
        .createTemporaryBackgroundTransform(mainPanel,
                                            bgImage,
                                            IdeBackgroundUtil.Fill.SCALE,
                                            IdeBackgroundUtil.Anchor.TOP_RIGHT,
                                            1f, JBInsets.emptyInsets(),
                                            parentDisposable)
    }
    return mainPanel
  }

  private fun textPaneContent(newBuild: BuildInfo, updatedChannel: UpdateChannel, appNames: ApplicationNamesInfo): @NlsSafe String {
    val style = UIUtil.getCssFontDeclaration(StartupUiUtil.labelFont)

    val message = newBuild.message
    val content = when {
      message.isNotBlank() -> message
      else -> IdeBundle.message("updates.new.version.available", appNames.fullProductName, downloadUrl(newBuild, updatedChannel))
    }

    return """<html><head>${style}</head><body>${content}</body></html>"""
  }

  private fun infoLabelText(newBuild: BuildInfo, patches: UpdateChain?, testPatch: Path?, appInfo: ApplicationInfo): @NlsContexts.DetailedDescription String {
    val patchSize = when {
      testPatch != null -> max(Files.size(testPatch) shr 20, 1).toString()
      patches != null && !patches.size.isNullOrBlank() -> {
        val size = patches.size!!
        val match = PATCH_SIZE_RANGE.matchEntire(size)
        if (match != null) match.groupValues[1] else size
      }
      else -> null
    }
    return when {
      patchSize != null -> IdeBundle.message("updates.from.to.size", appInfo.fullVersion, newBuild.version, newBuild.number.withoutProductCode(), patchSize)
      else -> IdeBundle.message("updates.from.to", appInfo.fullVersion, newBuild.version, newBuild.number.withoutProductCode())
    }
  }

  private fun smallFont(font: Font): Font = when (OS.CURRENT) {
    OS.macOS -> FontUtil.minusOne(font)
    OS.Linux -> FontUtil.minusOne(FontUtil.minusOne(font))
    else -> font
  }
}