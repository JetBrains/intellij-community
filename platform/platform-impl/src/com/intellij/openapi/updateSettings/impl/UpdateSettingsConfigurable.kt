// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.JBColor
import com.intellij.ui.layout.*
import com.intellij.util.text.DateFormatUtil
import java.awt.event.ActionListener
import javax.swing.JLabel

class UpdateSettingsConfigurable @JvmOverloads constructor (private val checkNowEnabled: Boolean = true) :
  BoundConfigurable(IdeBundle.message("updates.settings.title"), "preferences.updates") {

  override fun createPanel(): DialogPanel {
    val settings = UpdateSettings.getInstance()
    val manager = ExternalUpdateManager.ACTUAL
    val eapLocked = ApplicationInfoEx.getInstanceEx().isMajorEAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()
    val appInfo = ApplicationInfo.getInstance()
    val currentChannel = settings.selectedActiveChannel
    val channelModel = CollectionComboBoxModel(settings.activeChannels)
    var wasEnabled = settings.isCheckNeeded

    val warningsLabel = JLabel()
    val lastCheckedLabel = JLabel()

    updateLastCheckedLabel(lastCheckedLabel, settings.lastTimeChecked)

    return panel {
      row {
        cell(isFullWidth = true) {
          when {
            manager != null -> {
              checkBox(IdeBundle.message("updates.settings.checkbox.external"), settings.state::isCheckNeeded)
            }
            eapLocked -> {
              checkBox(IdeBundle.message("updates.settings.checkbox"), settings.state::isCheckNeeded)
              comboBox(channelModel, getter = { ChannelStatus.EAP }, setter = { }).enabled(false)
            }
            else -> {
              checkBox(IdeBundle.message("updates.settings.checkbox"), settings.state::isCheckNeeded)
              comboBox(channelModel,
                       getter = { settings.selectedActiveChannel },
                       setter = { settings.selectedChannelStatus = selectedChannel(it) })
                .also {
                  it.component.addActionListener(ActionListener {
                    warningsLabel.isVisible = selectedChannel(channelModel.selected) < currentChannel
                  })
                }
            }
          }

          if (checkNowEnabled) {
            button(IdeBundle.message("updates.settings.check.now.button")) {
              val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(lastCheckedLabel))
              val settingsCopy = UpdateSettings()
              settingsCopy.loadState(settings.state)
              settingsCopy.selectedChannelStatus = selectedChannel(channelModel.selected)
              UpdateChecker.updateAndShowResult(project, settingsCopy)
              updateLastCheckedLabel(lastCheckedLabel, settings.lastTimeChecked)
            }.withLargeLeftGap()
          }
        }
      }
      row {
        component(warningsLabel)
        when {
          manager != null -> {
            warningsLabel.text = IdeBundle.message("updates.settings.external", manager.toolName)
            warningsLabel.foreground = JBColor.GRAY
          }
          eapLocked -> {
            warningsLabel.text = IdeBundle.message("updates.settings.channel.locked")
            warningsLabel.foreground = JBColor.GRAY
          }
          else -> {
            warningsLabel.text = IdeBundle.message("updates.settings.unstable")
            warningsLabel.foreground = JBColor.RED
            warningsLabel.isVisible = false
          }
        }
      }

      row {
        row(IdeBundle.message("updates.settings.last.check")) { component(lastCheckedLabel).withLeftGap() }
        row(IdeBundle.message("updates.settings.current.version")) { label(ApplicationNamesInfo.getInstance().fullProductName + ' ' + appInfo.fullVersion).withLeftGap() }
        row(IdeBundle.message("updates.settings.build.number")) { label(appInfo.build.asString()).withLeftGap() }
          .largeGapAfter()
      }

      row {
        link(IdeBundle.message("updates.settings.ignored")) {
          val text = settings.ignoredBuildNumbers.joinToString("\n")
          val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(lastCheckedLabel))
          val result = Messages.showMultilineInputDialog(project, null, IdeBundle.message("updates.settings.ignored.title"), text, null,
                                                         null)
          if (result != null) {
            settings.ignoredBuildNumbers.clear()
            settings.ignoredBuildNumbers.addAll(result.split('\n'))
          }
        }
      }.largeGapAfter()

      row { checkBox(IdeBundle.message("updates.settings.show.editor"), settings.state::isShowWhatsNewEditor) }

      onGlobalApply {
        val isEnabled = settings.isCheckNeeded
        if (isEnabled != wasEnabled) {
          when {
            isEnabled -> UpdateCheckerComponent.getInstance().queueNextCheck()
            else -> UpdateCheckerComponent.getInstance().cancelChecks()
          }
          wasEnabled = isEnabled
        }
      }
    }
  }

  private fun selectedChannel(value: ChannelStatus?): ChannelStatus = value ?: ChannelStatus.RELEASE

  private fun updateLastCheckedLabel(label: JLabel, time: Long): Unit = when {
    time <= 0 -> label.text = IdeBundle.message("updates.last.check.never")
    else -> {
      label.text = DateFormatUtil.formatPrettyDateTime(time)
      label.toolTipText = DateFormatUtil.formatDate(time) + ' ' + DateFormatUtil.formatTimeWithSeconds(time)
    }
  }
}
