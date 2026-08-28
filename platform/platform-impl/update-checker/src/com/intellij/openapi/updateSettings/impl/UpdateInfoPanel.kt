// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import kotlin.math.max

private val DEFAULT_PREF_SIZE = JBDimension(540, 460)
private val PATCH_SIZE_RANGE: Regex = "from \\d+ to (\\d+)".toRegex()
private const val INCOMPATIBLE_PLUGINS_LIMIT = 2

private val REPORTING_LISTENER = object : BrowserHyperlinkListener() {
  override fun hyperlinkActivated(e: HyperlinkEvent) {
    UpdateInfoStatsCollector.click(e.description)
    super.hyperlinkActivated(e)
  }
}

@ApiStatus.Internal
fun downloadUrl(newBuild: BuildInfo, updatedChannel: UpdateChannel): String =
  IdeUrlTrackingParametersProvider.getInstance().augmentUrl(
    newBuild.downloadUrl ?: newBuild.blogPost ?: updatedChannel.url
    ?: ExternalProductResourceUrls.getInstance().downloadPageUrl?.toExternalForm() ?: ApplicationInfo.getInstance().companyURL
    ?: "https://www.jetbrains.com")

@ApiStatus.Internal
fun createUpdateInfoPanel(
  newBuild: BuildInfo,
  patches: UpdateChain?,
  testPatch: Path?,
  writeProtected: Boolean,
  licenseInfo: @NlsContexts.Label String?,
  licenseWarn: Boolean,
  incompatiblePluginNames: List<String>?,
  enableLink: Boolean,
  updatedChannel: UpdateChannel,
): DialogPanel {
  val appInfo = ApplicationInfo.getInstance()
  val appNames = ApplicationNamesInfo.getInstance()

  val textPane = JEditorPane("text/html", "").apply {
    border = JBUI.Borders.emptyRight(12)
    isEditable = false
    text = textPaneContent(newBuild, updatedChannel, appNames)
    caretPosition = 0 // set after text
    addHyperlinkListener(REPORTING_LISTENER)
    isOpaque = false
  }

  val scrollPane = ScrollPaneFactory.createScrollPane(textPane, true).apply {
    isOpaque = false
    viewport.isOpaque = false
    minimumSize = JBDimension(200, 100)
  }

  val infoPanel = panel {
    row {
      label(IdeBundle.message("updates.new.title", newBuild.displayVersion())).applyToComponent {
        font = JBFont.label().biggerOn(7f)
      }
    }.bottomGap(BottomGap.SMALL)
    row {
      cell(scrollPane).align(Align.FILL)
    }.resizableRow()
  }

  val additionalInfoPanel = panel {
    when {
      writeProtected ->
        banner(IdeBundle.message("updates.write.protected", appNames.productName, PathManager.getHomeDir()),
               EditorNotificationPanel.Status.Error)
      !incompatiblePluginNames.isNullOrEmpty() ->
        banner(getPluginsList(incompatiblePluginNames, newBuild.displayVersion()),
               EditorNotificationPanel.Status.Warning)
    }

    if (licenseInfo != null) {
      row {
        text(licenseInfo).applyToComponent {
          if (licenseWarn) {
            foreground = JBColor.RED
          }
        }.noBottomGap()
      }
    }
    row {
      text(infoLabelText(newBuild, patches, testPatch, appInfo))
        .applyToComponent {
          foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        }.noBottomGap()
    }
    if (enableLink) {
      row {
        link(IdeBundle.message("updates.configure.updates.label")) {
          ShowSettingsUtil.getInstance().editConfigurable(infoPanel, UpdateSettingsConfigurable(false))
        }.noBottomGap()
      }
    }
  }

  val result = panel {
    customizeSpacingConfiguration(EmptySpacingConfiguration()) {
      row {
        cell(infoPanel)
          .align(Align.FILL)
          .customize(UnscaledGaps(top = 20, left = PlatformUpdateDialog.LEFT_RIGHT_GAP, bottom = 6, right = 6))
      }.resizableRow()
      separator()
      row {
        cell(additionalInfoPanel)
          .align(AlignX.FILL)
          .customize(UnscaledGaps(top = 0,
                                  left = PlatformUpdateDialog.LEFT_RIGHT_GAP,
                                  bottom = 12,
                                  right = PlatformUpdateDialog.LEFT_RIGHT_GAP))
      }
    }
  }.apply {
    preferredSize = DEFAULT_PREF_SIZE
  }

  return result
}

private fun Panel.banner(messageText: @Nls String, status: EditorNotificationPanel.Status): Cell<InlineBanner> {
  lateinit var result: Cell<InlineBanner>
  row {
    result = cell(InlineBanner(messageText, status))
      .applyToComponent {
        showCloseButton(false)
        putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps(left = 8, right = 8))
      }
  }
  return result
}

/**
 * The version of the new build. Some update channels do not set it, so the build number is the fallback.
 */
private fun BuildInfo.displayVersion(): @NlsSafe String {
  return if (version.isBlank()) number.asStringWithoutProductCode() else version
}

private fun <T : JComponent> Cell<T>.noBottomGap(): Cell<T> {
  component.putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(bottom = false))
  return this
}

private fun getPluginsList(names: List<String>, version: String): @Nls String {
  val limitedPluginList = names.take(INCOMPATIBLE_PLUGINS_LIMIT).joinToString()
  val pluginsText = if (names.size <= INCOMPATIBLE_PLUGINS_LIMIT) limitedPluginList
  else
    IdeBundle.message("updates.incompatible.plugins.found.and.more",
                      limitedPluginList,
                      names.size - INCOMPATIBLE_PLUGINS_LIMIT)
  return IdeBundle.message("updates.incompatible.plugins.found", names.size, version, pluginsText)
}

private fun textPaneContent(newBuild: BuildInfo, updatedChannel: UpdateChannel, appNames: ApplicationNamesInfo): @NlsSafe String {
  val style = UIUtil.getCssFontDeclaration(StartupUiUtil.labelFont)

  val message = newBuild.message.trim()
  val content = when {
    message.isNotBlank() -> {
      val prefix = "<p>"
      // Paragraphs contain gap above, try to get rid of the first one
      if (message.startsWith(prefix, ignoreCase = true)) {
        "<p style='margin-top: 0'>" + message.substring(prefix.length)
      }
      else message
    }
    else -> IdeBundle.message("updates.new.version.available", appNames.fullProductName, downloadUrl(newBuild, updatedChannel))
  }

  return """<html><head>${style}</head><body>${content}</body></html>"""
}

private fun infoLabelText(
  newBuild: BuildInfo,
  patches: UpdateChain?,
  testPatch: Path?,
  appInfo: ApplicationInfo,
): @NlsContexts.Label String {
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
    patchSize != null -> IdeBundle.message("updates.from.to.size",
                                           appInfo.fullVersion,
                                           newBuild.displayVersion(),
                                           patchSize)
    else -> IdeBundle.message("updates.from.to", appInfo.fullVersion, newBuild.displayVersion())
  }
}
