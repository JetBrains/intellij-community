// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.io.File
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.math.max
import kotlin.math.min

object UpdateInfoPanelUI {
  private const val MB_UNITS = "MB"
  private const val PATCH_SIZE_IS = "Patch size is"
  private val FROM_TO_PATCHES_REGEXP: Regex = "from \\d+ to (\\d+)".toRegex()
  private val DIVIDER_COLOR = JBColor(0xd9d9d9, 0x515151)
  private const val DEFAULT_MIN_HEIGHT = 300
  private const val DEFAULT_MAX_HEIGHT = 600
  private const val DEFAULT_WIDTH = 700

  fun createPanel(newBuild: BuildInfo,
                  patches: UpdateChain?,
                  testPatch: File?,
                  writeProtected: Boolean,
                  licenseInfo: Pair<String, Color>?,
                  enableLink: Boolean,
                  updatedChannel: UpdateChannel): JPanel {
    val panel = JPanel(BorderLayout())

    val appInfo = ApplicationInfo.getInstance()
    val appNames = ApplicationNamesInfo.getInstance()

    val updateHighlightsComponent = object : JEditorPane("text/html", "") {}
      .also {
        val cssFontDeclaration = UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), null, null, null)
        val updateHighlightsContent = updateHighlightsContent(appNames, patches, testPatch, newBuild, updatedChannel)
        it.text = """<html><head>$cssFontDeclaration</head><body>$updateHighlightsContent</body></html>"""
        it.addHyperlinkListener(HyperlinkListener { event: HyperlinkEvent ->
          if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            FUCounterUsageLogger.getInstance().logEvent("ide.update.dialog", "link.clicked",
                                                        FeatureUsageData().addData("url", event.url.toString()))
          }
        })

        it.caretPosition = 0
        it.isEditable = false
        it.border = JBUI.Borders.empty(10, 16)
        it.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
      }

    val updateHighlightsScrollPane = ScrollPaneFactory.createScrollPane(updateHighlightsComponent, true)
      .also {
        it.border = JBUI.Borders.customLine(DIVIDER_COLOR, 0, 0, 1, 0)
        it.preferredSize = Dimension(min(it.preferredSize.width, DEFAULT_WIDTH),
                                     it.preferredSize.height.coerceIn(DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT))
      }

    val updatingVersionAndPatches = JBLabel()
      .also {
        it.border = JBUI.Borders.empty()
        it.foreground = SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.fgColor

        val patchSize = calculatePatchSize(patches, testPatch)
        it.text = """Updating ${appInfo.fullVersion} to ${newBuild.version} (${newBuild.number}).$patchSize"""
        it.font = smallFont(it.font)
      }

    val updatingInfoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
      .also {
        it.border = JBUI.Borders.empty(1, 16)
        it.add(updatingVersionAndPatches)
        getSettingsLink(panel, writeProtected, enableLink, appNames)?.let { link -> it.add(link) }
      }

    val infoPanel = JPanel(VerticalFlowLayout(0, 0))
      .also {
        it.border = JBUI.Borders.empty(8, 0)
        if (licenseInfo != null) {
          val licensePanel = JBLabel(licenseInfo.first)
            .also { label ->
              run {
                label.foreground = licenseInfo.second
                label.border = JBUI.Borders.empty(1, 16)
                label.font = smallFont(label.font)
              }
            }
          it.add(licensePanel)
        }
        it.add(updatingInfoPanel)
      }

    return panel
      .also {
        it.add(updateHighlightsScrollPane, BorderLayout.CENTER)
        it.add(infoPanel, BorderLayout.SOUTH)
      }
  }

  private fun smallFont(font: Font): Font = when {
    SystemInfoRt.isMac -> FontUtil.minusOne(font)
    SystemInfoRt.isLinux -> FontUtil.minusOne(FontUtil.minusOne(font))
    else -> font
  }

  private fun getSettingsLink(panel: JPanel, writeProtected: Boolean, enableLink: Boolean, appNames: ApplicationNamesInfo): LinkLabel<*>? {
    if (!enableLink) {
      return null
    }
    return if (writeProtected) {
      getConfigLink(panel, IdeBundle.message("updates.write.protected", appNames.productName, PathManager.getHomePath()))
        .also { it.foreground = JBColor.RED }
    }
    else {
      getConfigLink(panel, IdeBundle.message("updates.configure.updates.label"))
    }
  }

  private fun getConfigLink(panel: JPanel, text: String?): LinkLabel<*> =
    LinkLabel.create(text) { ShowSettingsUtil.getInstance().editConfigurable(panel, UpdateSettingsConfigurable(false)) }
      .also { it.font = smallFont(it.font) }

  private fun getPatchesText(patches: UpdateChain?, testPatch: File?): String? {
    return if (patches != null && !StringUtil.isEmptyOrSpaces(patches.size)) {
      patches.size
    }
    else if (testPatch != null) {
       max(1, testPatch.length() shr 20).toString()
    } else null
  }

  private fun updateHighlightsContent(appNames: ApplicationNamesInfo,
                                      patches: UpdateChain?,
                                      testPatch: File?,
                                      newBuildInfo: BuildInfo,
                                      updateChannel: UpdateChannel): String {
    var message = newBuildInfo.message
    if (message.isBlank()) {
      message = IdeBundle.message("updates.new.version.available", appNames.fullProductName, downloadUrl(newBuildInfo, updateChannel))
    }

    return "$message<br><br>" + newBuildInfo.buttons.filter { !it.isDownload }
      .joinToString("<br><br>") { "<a href=\"${it.url}\">${it.name}</href>" }
  }

  private fun calculatePatchSize(patchesChain: UpdateChain?, testPatch: File?): String {
    val patchesSize = getPatchesText(patchesChain, testPatch)
    return FROM_TO_PATCHES_REGEXP.matchEntire(patchesSize?: return "")?.let { " $PATCH_SIZE_IS about ${it.groupValues[1]} $MB_UNITS." }
           ?: " $PATCH_SIZE_IS $patchesSize $MB_UNITS."
  }

  private fun downloadUrl(newBuildInfo: BuildInfo, updateChannel: UpdateChannel): String {
    return IdeUrlTrackingParametersProvider.getInstance().augmentUrl(
      newBuildInfo.downloadUrl ?: newBuildInfo.blogPost ?: updateChannel.url ?: "https://www.jetbrains.com")
  }
}