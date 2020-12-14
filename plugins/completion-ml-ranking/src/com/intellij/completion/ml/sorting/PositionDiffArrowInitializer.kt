// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.application.options.CodeCompletionOptions
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.settings.MLCompletionSettingsCollector
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.completion.ml.util.language
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconManager
import com.intellij.ui.icons.RowIcon
import com.intellij.util.IconUtil
import icons.CompletionMlRankingIcons
import java.awt.Component
import java.awt.Graphics
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

class PositionDiffArrowInitializer : LookupTracker() {
  companion object {
    private const val DIFF_ICON_RIGHT_MARGIN = 4
    private const val SHOW_ARROWS_NOTIFICATION_REGISTRY = "completion.ml.show.arrows.notification"
    private const val ARROWS_NOTIFICATION_SHOWN_KEY = "completion.ml.arrows.notification.shown"
    private const val ARROWS_NOTIFICATION_AFTER_SESSIONS = 50
    private val POSITION_DIFF_KEY = Key.create<AtomicInteger>("PositionDiffArrowInitializer.POSITION_DIFF_KEY")
    private val POSITION_CHANGED_KEY = Key.create<Boolean>("PositionDiffArrowInitializer.POSITION_CHANGED_KEY")
    private val EMPTY_DIFF_ICON = IconManager.getInstance().createEmptyIcon(CompletionMlRankingIcons.ProposalUp)
    private val sessionsWithArrowsCounter = AtomicInteger()

    fun markAsReordered(lookup: LookupImpl, value: Boolean) {
      val changed = lookup.getUserData(POSITION_CHANGED_KEY)
      if (changed == null) {
        lookup.putUserData(POSITION_CHANGED_KEY, value)
        val language = lookup.language()
        if (value && language != null) {
          showArrowsNotificationIfNeeded(language)
        }
      }
    }

    fun itemPositionChanged(element: LookupElement, diffValue: Int) {
      val diff = element.getUserData(POSITION_DIFF_KEY) ?: AtomicInteger()
        .apply { element.putUserData(POSITION_DIFF_KEY, this) }

      diff.set(diffValue)
    }

    private fun shouldShowArrowsNotification(): Boolean = Registry.`is`(SHOW_ARROWS_NOTIFICATION_REGISTRY, true)

    private fun showArrowsNotificationIfNeeded(language: Language) {
      val experimentInfo = ExperimentStatus.getInstance().forLanguage(language)
      if (experimentInfo.inExperiment) return

      val properties = PropertiesComponent.getInstance()
      val mlRankingSettings = CompletionMLRankingSettings.getInstance()
      if (mlRankingSettings.isShowDiffEnabled && shouldShowArrowsNotification() && !properties.getBoolean(ARROWS_NOTIFICATION_SHOWN_KEY)) {
        val sessionsCount = sessionsWithArrowsCounter.incrementAndGet()
        if (sessionsCount == ARROWS_NOTIFICATION_AFTER_SESSIONS) {
          properties.setValue(ARROWS_NOTIFICATION_SHOWN_KEY, true)
          ArrowsOpinionNotification().notify(null)
        }
      }
    }
  }

  override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
    if (!shouldShowDiff(storage)) return

    lookup.addPresentationCustomizer(object : LookupCellRenderer.ItemPresentationCustomizer {
      override fun customizePresentation(item: LookupElement,
                                         presentation: LookupElementPresentation): LookupElementPresentation {
        val positionChanged = lookup.getUserData(POSITION_CHANGED_KEY)
        if (positionChanged == null || !positionChanged) return presentation
        val newPresentation = LookupElementPresentation()
        newPresentation.copyFrom(presentation)
        val diff = item.getUserData(POSITION_DIFF_KEY)?.get()
        val diffIcon = when {
          diff == null || diff == 0 -> EMPTY_DIFF_ICON
          diff < 0 -> CompletionMlRankingIcons.ProposalUp
          else -> CompletionMlRankingIcons.ProposalDown
        }
        val diffIconWithMargin = iconWithRightMargin(diffIcon)
        newPresentation.icon = ArrowDecoratedIcon(diffIconWithMargin, newPresentation.icon)
        return newPresentation
      }
    })
  }

  private fun shouldShowDiff(lookupStorage: LookupStorage): Boolean {
    val mlRankingSettings = CompletionMLRankingSettings.getInstance()
    return lookupStorage.model != null && mlRankingSettings.isShowDiffEnabled
  }

  class ArrowDecoratedIcon(private val arrowIcon: Icon, private val baseIcon: Icon?) :
    com.intellij.ui.RowIcon(2, RowIcon.Alignment.CENTER), LookupCellRenderer.IconDecorator {

    init {
      super.setIcon(arrowIcon, 0)
      super.setIcon(baseIcon, 1)
    }

    override fun getDelegate(): Icon? = baseIcon
    override fun withDelegate(icon: Icon?): LookupCellRenderer.IconDecorator = ArrowDecoratedIcon(arrowIcon, icon)
  }

  private class ArrowsOpinionNotification : Notification(
    MLCompletionBundle.message("ml.completion.notification.groupId"),
    MLCompletionBundle.message("ml.completion.notification.title"),
    MLCompletionBundle.message("ml.completion.notification.decorating.opinion.content"),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.like")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.LIKE)
          notification.expire()
        }
      })
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.dislike")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.DISLIKE)
          CompletionMLRankingSettings.getInstance().isShowDiffEnabled = false
          notification.expire()
          ArrowsDisabledNotification().notify(null)
        }
      })
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.neutral")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.NEUTRAL)
          notification.expire()
        }
      })
    }
  }

  private class ArrowsDisabledNotification : Notification(
    MLCompletionBundle.message("ml.completion.notification.groupId"),
    MLCompletionBundle.message("ml.completion.notification.title"),
    MLCompletionBundle.message("ml.completion.notification.decorating.disabled.content", ShowSettingsUtil.getSettingsMenuName()),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.disabled.configure")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, CodeCompletionOptions::class.java)
          notification.expire()
        }
      })
    }
  }

  private fun iconWithRightMargin(icon: Icon, margin: Int = DIFF_ICON_RIGHT_MARGIN): IconUtil.IconSizeWrapper {
    return object : IconUtil.IconSizeWrapper(icon, icon.iconWidth + margin, icon.iconHeight) {
      override fun paintIcon(icon: Icon?, c: Component?, g: Graphics?, x: Int, y: Int) {
        if (icon == null) return
        icon.paintIcon(c, g, x, y)
      }
    }
  }
}