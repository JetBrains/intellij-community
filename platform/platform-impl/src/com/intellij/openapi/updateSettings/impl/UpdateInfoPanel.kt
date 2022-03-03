// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.math.max
import kotlin.math.min

internal object UpdateInfoPanel {
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
  fun create(newBuild: BuildInfo,
             patches: UpdateChain?,
             testPatch: File?,
             writeProtected: Boolean,
             @NlsContexts.Label licenseInfo: String?,
             licenseWarn: Boolean,
             enableLink: Boolean,
             updatedChannel: UpdateChannel): JPanel {
    val appInfo = ApplicationInfo.getInstance()
    val appNames = ApplicationNamesInfo.getInstance()

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
      link.border = JBUI.Borders.empty(0, 4, 0, 0)
      link.font = smallFont(link.font)
      infoRow.add(link)
    }
    infoPanel.add(infoRow)

    val panel = JPanel(BorderLayout())
    panel.add(scrollPane, BorderLayout.CENTER)
    panel.add(infoPanel, BorderLayout.SOUTH)
    return panel
  }

  @NlsSafe
  private fun textPaneContent(newBuild: BuildInfo, updatedChannel: UpdateChannel, appNames: ApplicationNamesInfo): String {
    val style = UIUtil.getCssFontDeclaration(StartupUiUtil.getLabelFont())

    val message = newBuild.message
    val content = when {
      message.isNotBlank() -> message
      else -> IdeBundle.message("updates.new.version.available", appNames.fullProductName, downloadUrl(newBuild, updatedChannel))
    }

    return """<html><head>${style}</head><body>${content}</body></html>"""
  }

  @NlsContexts.DetailedDescription
  private fun infoLabelText(newBuild: BuildInfo, patches: UpdateChain?, testPatch: File?, appInfo: ApplicationInfo): String {
    val patchSize = when {
      testPatch != null -> max(testPatch.length() shr 20, 1).toString()
      patches != null && !patches.size.isNullOrBlank() -> {
        val match = PATCH_SIZE_RANGE.matchEntire(patches.size)
        if (match != null) match.groupValues[1] else patches.size
      }
      else -> null
    }
    return when {
      patchSize != null -> IdeBundle.message("updates.from.to.size", appInfo.fullVersion, newBuild.version, newBuild.number, patchSize)
      else -> IdeBundle.message("updates.from.to", appInfo.fullVersion, newBuild.version, newBuild.number)
    }
  }

  private fun smallFont(font: Font): Font = when {
    SystemInfo.isMac -> FontUtil.minusOne(font)
    SystemInfo.isLinux -> FontUtil.minusOne(FontUtil.minusOne(font))
    else -> font
  }

  @JvmStatic
  fun downloadUrl(newBuild: BuildInfo, updatedChannel: UpdateChannel): String =
    IdeUrlTrackingParametersProvider.getInstance().augmentUrl(
      newBuild.downloadUrl ?: newBuild.blogPost ?: updatedChannel.url ?: "https://www.jetbrains.com")
}
