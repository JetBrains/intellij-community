// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.WhatsNewAction
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.JBGridLayout
import com.intellij.ui.dsl.gridLayout.RowGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val TOOLBOX_URL =
  "https://www.jetbrains.com/toolbox-app/?utm_source=product&utm_medium=link&utm_campaign=toolbox_app_in_IDE_updatewindow&utm_content=we_recommend"

class UpdateSettingsConfigurable @JvmOverloads constructor (private val checkNowEnabled: Boolean = true) :
  BoundConfigurable(IdeBundle.message("updates.settings.title"), "preferences.updates") {

  private lateinit var myLink: JComponent
  private lateinit var myLastCheckedLabel: JLabel

  override fun createPanel(): DialogPanel {
    val settings = UpdateSettings.getInstance()
    val manager = ExternalUpdateManager.ACTUAL
    val eapLocked = ApplicationInfoEx.getInstanceEx().isMajorEAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()
    val appInfo = ApplicationInfo.getInstance()
    val channelModel = CollectionComboBoxModel(settings.activeChannels)

    return panel {
      row(IdeBundle.message("updates.settings.current.version") + ' ' + ApplicationNamesInfo.getInstance().fullProductName + ' ' + appInfo.fullVersion) {
        comment(appInfo.build.asString() + ' ' + NlsMessages.formatDateLong(appInfo.buildDate.time))
      }.bottomGap(BottomGap.SMALL)

      row {
        when {
          manager != null -> {
            comment(IdeBundle.message("updates.settings.external", manager.toolName))
          }
          eapLocked -> {
            checkBox(IdeBundle.message("updates.settings.checkbox"))
              .bindSelected(settings.state::isCheckNeeded)
            comment(IdeBundle.message("updates.settings.channel.locked"))
          }
          else -> {
            val checkBox = checkBox(IdeBundle.message("updates.settings.checkbox.for"))
              .bindSelected(settings.state::isCheckNeeded)
              .gap(RightGap.SMALL)
            comboBox(channelModel)
              .bindItem(getter = { settings.selectedActiveChannel },
                setter = { settings.selectedChannelStatus = selectedChannel(it) })
              .enabledIf(checkBox.selected)
          }
        }
      }

      row {
        checkBox(IdeBundle.message("updates.plugins.settings.checkbox"))
          .bindSelected(settings.state::isPluginsCheckNeeded)
      }

      row {
        if (checkNowEnabled) {
          button(IdeBundle.message("updates.settings.check.now.button")) {
            val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myLastCheckedLabel))
            val settingsCopy = UpdateSettings()
            settingsCopy.state.copyFrom(settings.state)
            settingsCopy.state.isCheckNeeded = true
            settingsCopy.state.isPluginsCheckNeeded = true
            settingsCopy.selectedChannelStatus = selectedChannel(channelModel.selected)
            UpdateChecker.updateAndShowResult(project, settingsCopy)
            updateLastCheckedLabel(settings.lastTimeChecked)
          }
        }

        myLastCheckedLabel = comment("").component
        updateLastCheckedLabel(settings.lastTimeChecked)
      }.topGap(TopGap.SMALL)
        .bottomGap(BottomGap.SMALL)

      if (WhatsNewAction.isAvailable()) {
        row {
          checkBox(IdeBundle.message("updates.settings.show.editor"))
            .bindSelected(settings.state::isShowWhatsNewEditor)
        }
      }

      if (settings.ignoredBuildNumbers.isNotEmpty()) {
        row {
          myLink = link(IdeBundle.message("updates.settings.ignored")) {
            val text = settings.ignoredBuildNumbers.joinToString("\n")
            val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myLink))
            val result = Messages.showMultilineInputDialog(project, null, IdeBundle.message("updates.settings.ignored.title"), text, null, null)
            if (result != null) {
              settings.ignoredBuildNumbers.clear()
              settings.ignoredBuildNumbers.addAll(result.split('\n'))
            }
          }.component
        }
      }

      if (!(manager == ExternalUpdateManager.TOOLBOX || Registry.`is`("ide.hide.toolbox.promo"))) {
        val panel = JPanel(JBGridLayout())
        val builder = RowsGridBuilder(panel).subGridBuilder(gaps = Gaps(top = JBUI.scale(6)))
        builder.cell(JBLabel(PluginLogo.reloadIcon(AllIcons.Nodes.Toolbox, 40, 40, null)),
          verticalAlign = VerticalAlign.TOP,
          gaps = Gaps(right = JBUI.scale(10)))
        val font = JBFont.label().asBold()
        builder.subGridBuilder()
          .cell(JBLabel(IdeBundle.message("updates.settings.recommend.toolbox.first.part") + " ")
            .withFont(font))
          .cell(BrowserLink(ExternalUpdateManager.TOOLBOX.toolName, TOOLBOX_URL)
            .withFont(font))
          .row(rowGaps = RowGaps(top = JBUI.scale(3)))
          .cell(MultiLineLabel(IdeBundle.message("updates.settings.recommend.toolbox.multiline.description")),
            width = 2)

        group(indent = false) {
          row {
            cell(panel)
          }
        }
      }

      var wasEnabled = settings.isCheckNeeded || settings.isPluginsCheckNeeded

      onApply {
        val isEnabled = settings.isCheckNeeded || settings.isPluginsCheckNeeded
        if (isEnabled != wasEnabled) {
          if (isEnabled) {
            UpdateCheckerService.getInstance().queueNextCheck()
          }
          else {
            UpdateCheckerService.getInstance().cancelChecks()
          }
          wasEnabled = isEnabled
        }
      }
    }
  }

  private fun selectedChannel(value: ChannelStatus?): ChannelStatus = value ?: ChannelStatus.RELEASE

  private fun updateLastCheckedLabel(time: Long): Unit =
    if (time > 0) {
      myLastCheckedLabel.text = IdeBundle.message("updates.settings.last.check", DateFormatUtil.formatPrettyDateTime(time))
      myLastCheckedLabel.toolTipText = DateFormatUtil.formatDate(time) + ' ' + DateFormatUtil.formatTimeWithSeconds(time)
    }
    else {
      myLastCheckedLabel.text = IdeBundle.message("updates.settings.last.check", IdeBundle.message("updates.last.check.never"))
      myLastCheckedLabel.toolTipText = null
    }
}
